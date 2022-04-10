import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

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
    private final MessageTemplate readyChildTemplate;
    private final MessageTemplate syncTemplate;
    private final ArrayList<StudentGroup> groups;
    private final HashSet<String> readyGroups;
    private final HashSet<String> syncGroups;
    private AID server;
    private final JSONArray results;
    private final String outputFile;

    public ClientBehaviour(Agent agent, String inputFile, String outputFile) {
        super(agent);
        this.step = 1;
        this.groups = MainReaderWriter.readStudents(inputFile);
        this.readyGroups = new HashSet<String>();
        this.syncGroups = new HashSet<String>();
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

        this.readyChildTemplate = new MessageTemplate((MatchExpression) message -> {
            String conversationID = message.getConversationId();
            // если информирование и результат
            return conversationID.equals("READY_GROUP");
        });

        this.syncTemplate = new MessageTemplate((MatchExpression) message -> {
            String conversationID = message.getConversationId();
            // если информирование и результат
            return conversationID.equals("SYNC_GROUP");
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
                var resultRequest = this.myAgent.receive(this.readyChildTemplate);
                if (resultRequest != null) {
                    var group = resultRequest.getContent();
                    if (resultRequest.getPerformative() == ACLMessage.AGREE) {
                        readyGroups.add(group);
                    } else {
                        readyGroups.remove(group);
                    }
                    Output((resultRequest.getPerformative() == ACLMessage.AGREE ? "Готов " : "Не готов ")
                            + group + " " + readyGroups.size() + "/" + groups.size());

                    if (readyGroups.size() >= groups.size()) {
                        var mes = new ACLMessage();
                        mes.addReceiver(this.server);
                        mes.setConversationId("RESULT_CLIENT");
                        this.myAgent.send(mes);
                        Output("send RESULT_CLIENT");

                        requestTimetable();
                        nextStep();
                    }
                    Output("Синхронизировано также " + syncGroups.size());
                    if (syncGroups.size() >= (groups.size() - readyGroups.size())) {
                        syncGroups.clear();
                        allRequest("SYNC_GROUP");
                    }
                }

                var syncRequest = this.myAgent.receive(this.syncTemplate);
                if (syncRequest != null) {
                    var group = syncRequest.getContent();
                    if (syncRequest.getPerformative() == ACLMessage.AGREE) {
                        syncGroups.add(group);
                    } else {
                        syncGroups.remove(group);
                    }
                    Output((syncRequest.getPerformative() == ACLMessage.AGREE ? "Синхронизирован " : "Не синхронизирован ")
                            + group + " " + syncGroups.size() + "/" + (groups.size() - readyGroups.size()));

                    Output("Не синхронизированы: " + notSyncedGroup().toString());

                    if (syncGroups.size() >= (groups.size() - readyGroups.size())) {
                        syncGroups.clear();
                        allRequest("SYNC_GROUP");
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
                    MainReaderWriter.writeCsvClient(results, this.outputFile);
                    finish();
                }
            }
        }
    }

    ArrayList<String> notSyncedGroup() {
        return new ArrayList(groups.stream().filter(group ->
                !syncGroups.contains(group.name)
        ).toList());
    }

    int allRequest(String conversation) {
        var groups = DFUtilities.searchService(this.myAgent, "group");

        var mes = new ACLMessage();
        mes.setConversationId(conversation);
        for (AID group : groups) {
            mes.addReceiver(group);
        }
        this.myAgent.send(mes);

        return groups.length;
    }

    void requestTimetable() {
        var length = allRequest("TIMETABLE_GROUP");
        Output("Запрошены результаты у " + length + " групп");
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