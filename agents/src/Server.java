import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import utils.*;

public class Server extends Agent {
    protected void setup() {
        // регистрируемся как сарвар
        DFUtilities.register(this, "server");

        Object[] args = getArguments();

        // читаем учителей и аудитории
        var teachers = MainReaderWriter.readTeacher(args[0].toString());
        var auditoriums = MainReaderWriter.readAuditoriums(args[0].toString());
        var outputFile = args[1].toString();
        Output((String)args[0]);

        // поведение
        addBehaviour(new ServerBehaviour(this, teachers, auditoriums, outputFile));

        // стартуем агентов
        var container = this.getContainerController();
        teachers.forEach(teacher -> {
            try {
                Object[] agentArgs = new Object[2];
                agentArgs[0] = teacher;
                agentArgs[1] = this.getAID();

                var agent = container.createNewAgent(teacher.name, "TeacherAgent", agentArgs);
                agent.start();
            } catch (Exception e) { e.printStackTrace(); }
        });
        auditoriums.forEach(auditorium -> {
            try {
                Object[] agentArgs = new Object[2];
                agentArgs[0] = auditorium;
                agentArgs[1] = this.getAID();

                var agent = container.createNewAgent(auditorium.name, "AuditoriumAgent", agentArgs);
                agent.start();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void Output(String message) {
        System.out.println(getLocalName() + ": " + message);
    }

    protected void takeDown() {
        Container.Kill(this);
    }
}

class ServerBehaviour extends SimpleBehaviour {
    private final MessageTemplate readyTemplate;
    private final MessageTemplate childReadyTemplate;
    private final MessageTemplate resultTemplate;
    private final MessageTemplate resultTeacherTemplate;
    private final MessageTemplate resultAuditoriumTemplate;

    private int readyClients = 0;
    private int readyChildren = 0;
    private int step = 1;

    ArrayList<Teacher> teachers;
    ArrayList<Auditorium> auditoriums;

    JSONObject results = new JSONObject();
    String outputFile;

    public ServerBehaviour(Agent agent, ArrayList<Teacher> teachers, ArrayList<Auditorium> auditoriums, String outputFile) {
        super(agent);

        results.put("teacher", new JSONArray());
        results.put("auditorium", new JSONArray());

        this.outputFile = outputFile;
        this.teachers = teachers;
        Output("teachers: " + teachers.toString());
        this.auditoriums = auditoriums;
        Output("auditoriums: " + auditoriums.toString());

        // описание шаблона для получения сообщения от клиента
        this.readyTemplate = new MessageTemplate(message -> {
            var conversation_Id = message.getConversationId();
            if (conversation_Id == null)
                return false;
            return conversation_Id.equals("READY_SERVER");
        });

        this.childReadyTemplate = new MessageTemplate(message -> {
            var conversation_Id = message.getConversationId();
            if (conversation_Id == null)
                return false;
            return conversation_Id.equals("READY_CHILD");
        });

        this.resultTemplate = new MessageTemplate((message) -> {
            var conversation_Id = message.getConversationId();
            if (conversation_Id == null)
                return false;
            return conversation_Id.equals("RESULT_CLIENT");
        });

        this.resultTeacherTemplate = new MessageTemplate((message) -> {
            var conversation_Id = message.getConversationId();
            if (conversation_Id == null)
                return false;
            return conversation_Id.equals("TIMETABLE_TEACHER");
        });

        this.resultAuditoriumTemplate = new MessageTemplate((message) -> {
            var conversation_Id = message.getConversationId();
            if (conversation_Id == null)
                return false;
            return conversation_Id.equals("TIMETABLE_AUDITORIUM");
        });
    }

    private void Output(String message) {
        System.out.println(this.myAgent.getLocalName() + ": " + message);
    }

    @Override
    public void action() {
        switch (step) {
            case 1 -> {
                // ждем запрос от клиента или учителей/аудиторий
                var readyRequest = this.myAgent.receive(this.readyTemplate);
                var childReadyRequest = this.myAgent.receive(this.childReadyTemplate);
                var resultRequest = this.myAgent.receive(this.resultTemplate);

                if (readyRequest != null) {
                    // готовность да/нет
                    var client = readyRequest.getSender();
                    Output("got a request ready from " + client.getName());

                    var reply = readyRequest.createReply();
                    if (readyChildren >= (teachers.size() + auditoriums.size())) {
                        reply.setPerformative(ACLMessage.AGREE);
                    } else {
                        reply.setPerformative(ACLMessage.CANCEL);
                    }
                    reply.setConversationId("READY_SERVER");
                    this.myAgent.send(reply);
                }
                if (childReadyRequest != null) {
                    // преподаватель/аудитория готова
                    readyChildren++;
                    Output("child ready " + readyChildren + "/" + (teachers.size() + auditoriums.size()));
                }
                if (resultRequest != null) {
                    var clients = DFUtilities.searchService(this.myAgent, "client");
                    this.readyClients++;

                    if (this.readyClients >= clients.length) {
                        Output("clients ready " + readyClients + "/" + clients.length);
                        Output("SERVER FINISHED");
                        requestTimetable();
                        this.step = 2;
                    }
                }
            }
            case 2 -> {
                var teacherRequest = this.myAgent.receive(this.resultTeacherTemplate);
                var auditoriumRequest = this.myAgent.receive(this.resultAuditoriumTemplate);

                var parser = new JSONParser();
                var teacherResults = (JSONArray)results.get("teacher");
                var auditoriumResults = (JSONArray)results.get("auditorium");
                try {
                    if (teacherRequest != null) {
                        teacherResults.add((JSONObject)parser.parse(teacherRequest.getContent()));
                        Output("Пришли результаты от преподавателя " + teacherRequest.getSender().getName()
                                + " " + teacherResults.size() + "/" + teachers.size());
                    }
                    if (auditoriumRequest != null) {
                        auditoriumResults.add((JSONObject)parser.parse(auditoriumRequest.getContent()));
                        Output("Пришли результаты от аудитории " + auditoriumRequest.getSender().getName()
                                + " " + auditoriumResults.size() + "/" + auditoriums.size());
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                if (teacherResults.size() >= teachers.size() && auditoriumResults.size() >= auditoriums.size()) {
                    MainReaderWriter.writeCsvServer(results, this.outputFile);
                    Container.Kill(myAgent);

                    this.step = 0;
                }
            }
        }

    }

    void requestTimetable() {
        var teachers = DFUtilities.searchService(this.myAgent, "teacher");
        var auditoriums = DFUtilities.searchService(this.myAgent, "auditorium");

        Output("Запрос результатов у " + teachers.length + " преподавателей и " + auditoriums.length + " аудиторий");

        var mes = new ACLMessage();
        mes.setConversationId("TIMETABLE_TEACHER");
        for (AID teacher : teachers) {
            mes.addReceiver(teacher);
        }
        this.myAgent.send(mes);

        mes.clearAllReceiver();
        mes.setConversationId("TIMETABLE_AUDITORIUM");
        for (AID auditorium : auditoriums) {
            mes.addReceiver(auditorium);
        }
        this.myAgent.send(mes);
    }

    @Override
    public boolean done() {
        return this.step == 0;
    }
}