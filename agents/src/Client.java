import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.MessageTemplate.MatchExpression;
import jade.core.behaviours.SimpleBehaviour;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import utils.*;

public class Client extends Agent {
    protected void setup() {
        Object[] args = getArguments();
        String inputFile = (String)args[0];
        String outputFile = (String)args[1];

        // регистрируемся как клиент
        DFUtilities.register(this, "client");

        // поведение
        addBehaviour(new ClientBehaviour(this, inputFile, outputFile));
    }

    protected void takeDown() {
        Container.Kill(this);
        System.out.println("Закрытие клиента..");
    }
}

class ClientBehaviour extends SimpleBehaviour {
    private int step;
    private final MessageTemplate readyServerTemplate;
    private final MessageTemplate resultTemplate;
    private final ArrayList<StudentGroup> groups;
    private AID server;
    private final JSONArray results;
    private final String outputFile;

    public ClientBehaviour(Agent agent, String inputFile, String outputFile) {
        super(agent);
        this.step = 1;
        this.groups = MainReaderWriter.readStudents(inputFile);
        this.results = new JSONArray();
        this.outputFile = outputFile;

        // шаблон сообщение
        this.readyServerTemplate = new MessageTemplate((MatchExpression) message -> {
            String conversationID = message.getConversationId();
            // если информирование и результат
            return conversationID.equals("READY_SERVER");
        });

        this.resultTemplate = new MessageTemplate((MatchExpression) message -> {
            String conversationID = message.getConversationId();
            // если информирование и результат
            return conversationID.equals("TIMETABLE_GROUP");
        });

        // ждем запуск сервера
        var flag = true;
        while (flag) {
            var serverIds = DFUtilities.searchService(myAgent, "server");
            if (serverIds.length > 0) {
                this.server = serverIds[0];

                // стартовое сообщение
                var mes = new ACLMessage();
                mes.addReceiver(this.server);
                mes.setConversationId("READY_SERVER");
                this.myAgent.send(mes);
                Output("send READY_SERVER?");

                flag = false;
            } else {
                // блок кода бесполезен
                try
                {
                    Output("server not found");
                    Thread.sleep(2000);
                }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private void handleException(Exception e) {
        e.printStackTrace();
        this.myAgent.doDelete();
        this.step = 0;
    }

    private void nextStep() {
        this.step++;
    }

    private void finish() {
        this.step = 0;
    }

    private void Output(String prefix, String message) {
        System.out.println("[" + prefix + "]: " + message);
    }

    private void Output(String message) {
        Output(this.myAgent.getLocalName(), message);
    }

    @Override
    public void action() {
        switch (this.step) {
            case 1 -> {
                // спрашиваем готовность сервера
                var serverReply = this.myAgent.receive(readyServerTemplate);
                if (serverReply != null && serverReply.getPerformative() == ACLMessage.AGREE) {
                    // можно плодить студентов
                    var container = myAgent.getContainerController();
                    groups.forEach(group -> {
                        try {
                            Object[] agentArgs = new Object[2];
                            agentArgs[0] = group;
                            agentArgs[1] = myAgent.getAID();

                            var agent = container.createNewAgent(group.name, "StudentGroupAgent", agentArgs);
                            agent.start();
                        } catch (Exception e) { e.printStackTrace(); }
                    });

                    nextStep();
                } else {
                    // повторяем через 3 сек запрос
                    try
                    {
                        Output("server not ready");
                        Thread.sleep(3000);
                    }
                    catch(InterruptedException ex) { Thread.currentThread().interrupt(); }

                    var mes = new ACLMessage();
                    mes.addReceiver(this.server);
                    mes.setConversationId("READY_SERVER");
                    this.myAgent.send(mes);
                }
            }
            case 2 -> {
                var flag = true;
                while (flag) {
                    var readyGroups = DFUtilities.searchService(myAgent, "groupReady");

                    if (readyGroups.length >= this.groups.size()) {
                        // стартовое сообщение
                        var mes = new ACLMessage();
                        mes.addReceiver(this.server);
                        mes.setConversationId("RESULT_CLIENT");
                        this.myAgent.send(mes);
                        Output("send RESULT_CLIENT");

                        flag = false;

                        requestTimetable();
                        nextStep();
                    } else {
                        // блок кода бесполезен
                        try
                        {
                            Thread.sleep(5000);
                        }
                        catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
                    }
                }
            }
            case 3 -> {
                var resultRequest = this.myAgent.receive(this.resultTemplate);
                var parser = new JSONParser();
                try {
                    if (resultRequest != null) {
                        results.add((JSONObject)parser.parse(resultRequest.getContent()));
                        Output("Пришли результаты от группы " + resultRequest.getSender().getName()
                                + " " + results.size() + "/" + groups.size());
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                if (results.size() >= groups.size()) {
                    MainReaderWriter.write(results, this.outputFile);
                    Container.Kill(myAgent);
                    finish();
                }
            }
        }
    }

    void requestTimetable() {
        var groups = DFUtilities.searchService(this.myAgent, "group");

        Output("Запрос результатов у " + groups.length + " групп");

        var mes = new ACLMessage();
        mes.setConversationId("TIMETABLE_GROUP");
        for (AID group : groups) {
            mes.addReceiver(group);
        }
        this.myAgent.send(mes);
    }

    @Override
    public boolean done() {
        return this.step == 0;
    }

    @Override
    public int onEnd() {
        this.myAgent.doDelete();
        return super.onEnd();
    }
}