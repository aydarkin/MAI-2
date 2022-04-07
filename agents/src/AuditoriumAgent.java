import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import org.json.simple.JSONObject;
import utils.DFUtilities;

import java.io.IOException;
import java.util.ArrayList;

public class AuditoriumAgent extends Agent {
    public Auditorium model;
    public AID server;

    protected void setup() {
        Object[] args = getArguments();
        model = (Auditorium) args[0];
        server = (AID)args[1];

        // регистрируемся
        var services = new ArrayList<String>();
        services.add("auditorium");
        services.add(model.type == LessonType.LECTURE ? "auditoriumLecture" : "auditoriumPractice");

        DFUtilities.register(this, services);

        addBehaviour(new AuditoriumBehaviour(this, model, server));
    }
}

class AuditoriumBehaviour extends SimpleBehaviour {
    public Auditorium model;
    public AID server;
    public int step = 1;

    public AuditoriumBehaviour(Agent agent, Auditorium model, AID server) {
        super(agent);
        this.model = model;
        this.server = server;

        // готов
        var mes = new ACLMessage();
        mes.addReceiver(this.server);
        mes.setConversationId("READY_CHILD");
        this.myAgent.send(mes);
    }

    private void print(String message) {
        System.out.println(this.myAgent.getLocalName() + ": " + message);
    }

    @Override
    public void action() {
        var mes = this.myAgent.receive();
        if (mes != null) {
            print("Получено сообщение от " + mes.getSender().getName() + " " + mes.getConversationId());
            var reply = mes.createReply();
            try {
                switch (mes.getConversationId()) {
                    case "TIMETABLE_AUDITORIUM" -> {
                        var obj = new JSONObject();
                        obj.put("auditorium", model.name);
                        obj.put("timetable", model.getResultTimeTable());

                        reply.setContent(obj.toJSONString());
                        print("Шлем результаты серверу");
                        myAgent.send(reply);
                    }
                    case "available" -> {
                        var obj = (MyMessage) mes.getContentObject();
                        obj.payload = model.getAvailable();

                        reply.setContentObject(obj);
                        myAgent.send(reply);
                    }
                    case "proposal" -> {
                        // читаем группу, день, урок
                        var myMsg = (MyMessage) mes.getContentObject();
                        var obj = (ArrayList<String>) myMsg.payload;
                        var group = obj.get(0);
                        var day = Integer.parseInt(obj.get(1));
                        var lesson = Integer.parseInt(obj.get(2));
                        var subject = obj.get(3);
                        var type = Integer.parseInt(obj.get(4));
                        var studentCount = Integer.parseInt(obj.get(5));

                        // предложение
                        if (model.isBusy(day, lesson) || !model.canOccupation(type, studentCount)) {
                            // todo возможно нужно добавить force версию
                            myMsg.payload = model.getAvailable();
                            reply.setContentObject(myMsg);

                            // FAILURE - если не подходит тип
                            // CANCEL - если занято
                            reply.setPerformative(model.canOccupation(day, lesson)
                                    ? ACLMessage.CANCEL
                                    : ACLMessage.FAILURE);
                        } else {
                            reply.setPerformative(ACLMessage.AGREE);

                            // вместо имени группы записываем свое имя
                            obj.set(0, model.name);
                            reply.setContentObject(obj);

                            // записываем группу в календарь
                            model.timeTable.get(day).get(lesson).group = group;
                            model.timeTable.get(day).get(lesson).type = type;
                            model.timeTable.get(day).get(lesson).subject = subject;
                        }
                        myAgent.send(reply);
                    }
                    case "whatGroup" -> {
                        //fixme копировать из учитэля



                        // читаем день, урок
                        var myMsg = (MyMessage) mes.getContentObject();
                        var obj = (ArrayList<String>) myMsg.payload;
                        var day = Integer.parseInt(obj.get(0));
                        var lesson = Integer.parseInt(obj.get(1));

                        reply.setContent(model.whatGroup(day,lesson));
                        myAgent.send(reply);
                    }
                    case "cancelLesson" -> {
                        // читаем группу, день, урок
                        var obj = (ArrayList<String>) mes.getContentObject();
                        var group = obj.get(0);
                        var day = Integer.parseInt(obj.get(1));
                        var lesson = Integer.parseInt(obj.get(2));

                        // отменить можно только свою пару
                        var occupation = model.timeTable.get(day).get(lesson);
                        if (occupation.group.equals(group)) {
                            occupation.group = null;
                        }
                    }
                    case "transferLesson" -> {
                        // читаем группу, день, урок
                        var obj = (ArrayList<String>) mes.getContentObject();
                        var group = obj.get(0);
                        var day = Integer.parseInt(obj.get(1));
                        var lesson = Integer.parseInt(obj.get(2));
                        var newDay = Integer.parseInt(obj.get(3));
                        var newLesson = Integer.parseInt(obj.get(4));

                        // отменить можно только свою пару
                        reply.setPerformative(ACLMessage.CANCEL);
                        var occupation = model.timeTable.get(day).get(lesson);
                        if (occupation.group.equals(group)) {
                            occupation.group = null;

                            var newOccupation = model.timeTable.get(newDay).get(newLesson);
                            if (newOccupation.group == null) {
                                newOccupation.group = group;
                                reply.setPerformative(ACLMessage.AGREE);
                            }
                        }
                        myAgent.send(reply);
                    }
                    case "selectTeacher" -> {
                        // читаем аудиторию, день, урок
                        var obj = (ArrayList<String>) mes.getContentObject();
                        var teacher = obj.get(0);
                        var day = Integer.parseInt(obj.get(1));
                        var lesson = Integer.parseInt(obj.get(2));

                        model.timeTable.get(day).get(lesson).teacher = teacher;
                    }
                }
            } catch (IOException | UnreadableException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean done() {
        return this.step == 0;
    }
}