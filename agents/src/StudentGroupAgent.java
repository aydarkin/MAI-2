import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import org.json.simple.JSONObject;
import utils.DFUtilities;

import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

public class StudentGroupAgent extends Agent {
    public StudentGroup model;
    public AID client;
    public HashSet<String> statuses = new HashSet<String>();

    protected void setup() {
        Object[] args = getArguments();
        model = (StudentGroup) args[0];
        client = (AID)args[1];

        // регистрируемся
        DFUtilities.register(this, "group");

        addBehaviour(new StudentGroupBehaviour(this, model, client));
    }

    void setReady(boolean isReady) {
        if (isReady) {
            statuses.add("groupReady");
        } else {
            statuses.remove("groupReady");
        }
        setStatuses();
    }

    void setExchangeReady(boolean isReady) {
        if (isReady) {
            statuses.add("groupExchangeReady");
        } else {
            statuses.remove("groupExchangeReady");
        }
        setStatuses();
    }

    void setStatuses() {
        var services = new ArrayList<String>();
        services.add("group");
        services.addAll(statuses);

        DFUtilities.deregister(this);
        DFUtilities.register(this, services);
    }
}

class StudentGroupBehaviour extends SimpleBehaviour {
    public StudentGroup model;
    public AID client;
    public int step = 1;

    ArrayList<String> askedTeachers = new ArrayList<String>();
    ArrayList<String> askedSubjects = new ArrayList<String>();
    ArrayList<String> askedAuditorium = new ArrayList<String>();

    ArrayList<SimpleEntry<Integer, Integer>> droppedLesson = new ArrayList<SimpleEntry<Integer, Integer>>();

    public StudentGroupBehaviour(Agent agent, StudentGroup model, AID server) {
        super(agent);
        this.model = model;
        this.client = server;

        print("Готов");

        // начало
        try {
            askNextSubject(null);
        } catch (IOException e) {}
    }

    private void print(String message) {
        System.out.println(this.myAgent.getLocalName() + ": " + message);
    }



    @Override
    public void action() {
        var mes = this.myAgent.receive();
        if (mes != null) {
            try {
                print("Получено сообщение от " + mes.getSender().getName() + " " + mes.getConversationId());
                switch (mes.getConversationId()) {
                    case "TIMETABLE_GROUP" -> {
                        var obj = new JSONObject();
                        obj.put("group", model.name);
                        obj.put("timetable", model.getResultTimeTable());

                        var reply = mes.createReply();
                        reply.setContent(obj.toJSONString());
                        myAgent.send(reply);
                    }
                    case "available" -> {
                        switch (step) {
                            case 1 -> availableTeacherHandler(mes, false);
                            case 2 -> {
                                // пока единый обработчик
                                // ниже как пример

                                // получили расписание для обмена
                                var myMsg = (MyMessage) mes.getContentObject();
                                var obj = (ArrayList<String>) myMsg.payload;
                                if (myMsg.group != null) {
                                    // ответ для переноса по просьбе другой группы
                                    // transferLesson
                                    // шлем в ответ exchange=AGREE
                                } else {
                                    // ответ для начала переноса
                                    var availableInfo = (ArrayList<SimpleEntry<Integer, Integer>>) myMsg.payload;

                                    // нарастить метод
                                    var selected = getFirstIntersection(availableInfo);

                                    // если есть, предложить
                                    //
                                }
                            }
                        }
                    }
                    case "proposal" -> {
                        switch (step) {
                            case 1 -> proposalTeacherHandler(mes, false);
                            case 2 -> {
                                // proposalTeacherHandler возможно подойдет
                            }
                        }

                    }
                    case "availableForce" -> {
                        if (step == 1) {
                            availableTeacherHandler(mes, true);
                        }
                    }
                    case "proposalForce" -> {
                        if (step == 1) {
                            proposalTeacherHandler(mes, true);
                        }
                    }
                    case "exchange" -> {
                        var myMsg = (MyMessage) mes.getContentObject();
                        var obj = (ArrayList<String>) myMsg.payload;
                        var day = Integer.parseInt(obj.get(0));
                        var lesson = Integer.parseInt(obj.get(1));
                        switch (mes.getPerformative()) {
                            case ACLMessage.REQUEST -> {
                                // получен запрос на обмен
                                var teacher = model.timeTable.get(day).get(lesson).teacher;
                                if (teacher != null) {
                                    // спрашиваем расписание
                                    var msg = new ACLMessage();
                                    msg.addReceiver(findTeacher(teacher));
                                    msg.setConversationId("available");
                                    msg.setContentObject(myMsg);
                                    this.myAgent.send(msg);
                                } else {
                                    var reply = mes.createReply();
                                    reply.setPerformative(ACLMessage.CANCEL);
                                    this.myAgent.send(reply);
                                }
                            }
                            case ACLMessage.AGREE -> {
                                // получен ответ на запрос об обмене
                                iterationExchange();
                            }
                            case ACLMessage.CANCEL -> {
                                // получен отрицательный ответ на запрос об обмене
                                droppedLesson.add(new SimpleEntry<Integer, Integer>(day, lesson));
                                iterationExchange();
                            }
                        }
                    }
                    case "whatGroup" -> {
                        // ответ от преподавателя
                        var myMsg = (MyMessage) mes.getContentObject();
                        var obj = (ArrayList<String>) myMsg.payload;
                        var day = Integer.parseInt(obj.get(0));
                        var lesson = Integer.parseInt(obj.get(1));
                        if (mes.getPerformative() == ACLMessage.AGREE) {
                            var groupName = myMsg.group;

                            var msg = new ACLMessage();
                            msg.addReceiver(findGroup(groupName));
                            msg.setConversationId("exchange");
                            msg.setPerformative(ACLMessage.REQUEST);
                            msg.setContentObject(myMsg);
                            this.myAgent.send(msg);
                        } else {
                            // занят
                            droppedLesson.add(new SimpleEntry<Integer, Integer>(day, lesson));
                            iterationExchange();
                        }
                    }
                    case "transferLesson" -> {
                        if (step == 1) {

                        }
                    }
                }
            } catch (IOException | UnreadableException e) {
                e.printStackTrace();
            }
        }
    }

    AID findGroup(String name) {
        var groups = DFUtilities.searchService(myAgent, "group");
        for (AID group : groups) {
            if (group.getName().contains(name)) {
                return group;
            }
        }
        return null;
    }

    AID findTeacher(String name) {
        var teachers = DFUtilities.searchService(myAgent, "teacher");
        for (AID teacher : teachers) {
            if (teacher.getName().contains(name)) {
                return teacher;
            }
        }
        return null;
    }

    void availableTeacherHandler(ACLMessage mes, boolean force) throws UnreadableException, IOException  {
        var myMsg = (MyMessage) mes.getContentObject();
        var availableInfo = (ArrayList<SimpleEntry<Integer, Integer>>) myMsg.payload;
        var selected = getFirstIntersection(availableInfo);
        var subject = getFirstRemainingLessonForTeacher();
        if (selected != null && subject.isPresent()) {
            print("Есть пересечение " + selected.toString());
            // шлем запрос
            var obj = new ArrayList<String>(4);
            obj.add(model.name);
            obj.add(selected.getKey().toString());
            obj.add(selected.getValue().toString());

            // первый предмет из очереди
            obj.add(subject.get().name);
            obj.add(Integer.toString(subject.get().type));
            myMsg.payload = obj;

            var reply = mes.createReply();
            reply.setContentObject(myMsg);
            reply.setConversationId(force ? "proposalForce" : "proposal");
            myAgent.send(reply);
        } else if (subject.isPresent()) {
            if (this.step == 1) {
                print("Нет пересечения. Спрашиваем следующего преподавателя");
                askedTeachers.add(mes.getSender().getName());
                var asked = askNextTeacher(subject.get().name);
                if (!asked) {
                    print("Преподаватели кончились. Спрашиваем следующий предмет");
                    var askedSubject = askNextSubject(subject.get().name);
                    if (!askedSubject) {
                        print("Первичное распределение завершено");
                        print("Мне осталось: " + model.getRemainingLessonsForTeacher());

                        startExchange();

                        // для теста первого этапа
                        //setReady(true);
                    }
                }
            } else if (this.step == 2) {
                print("Нет пересечения. Спрашиваем кто занял пару");

                var msg = mes.createReply();
                msg.setConversationId("whatGroup");
                msg.setContentObject(myMsg);
                this.myAgent.send(msg);
            }
        } else {
            // нет больше предметов
            print("Кончились предметы");
            setReady(true);
        }
    }

    void askWhatGroup() {

    }

    void proposalTeacherHandler(ACLMessage mes, boolean force) throws IOException, UnreadableException {
        var performative = mes.getPerformative();
        if (performative == ACLMessage.AGREE) {
            // преподаватель установлен
            // читаем группу, день, урок
            var myMsg = (MyMessage) mes.getContentObject();
            var obj = (ArrayList<String>) myMsg.payload;
            var teacher = obj.get(0);
            var day = Integer.parseInt(obj.get(1));
            var lesson = Integer.parseInt(obj.get(2));
            var subject = obj.get(3);
            var type = Integer.parseInt(obj.get(4));

            print("Добавлен преподаватель " + teacher + " на " + day +" " + lesson);
            model.timeTable.get(day).get(lesson).teacher = teacher;
            model.timeTable.get(day).get(lesson).subject = subject;
            model.timeTable.get(day).get(lesson).type = type;

            if (step == 1) {
                // спрашиваем дальше
                var askedSubject = askNextSubject(null);
                if (!askedSubject) {
                    print("Первичное распределение завершено после очередного заполнения");
                    print("Мне осталось: " + model.getRemainingLessonsForTeacher());

                    // начинаем обмен
                    startExchange();

                    // для теста первого этапа
                    // setReady(true);
                }
            }

        } else {
            if (step == 1) {
                // повторяем предложение
                availableTeacherHandler(mes, force);
            }
        }
    }

    void startExchange() throws IOException {
        this.step = 2;
        setExchangeReady(true);

        print("Синхронизация");
        // синхронизация
        var flag = true;
        while (flag) {
            var readyGroups = DFUtilities.searchService(myAgent, "groupExchangeReady");
            var allGroups = DFUtilities.searchService(myAgent, "group");
            if (readyGroups.length >= allGroups.length) {
                flag = false;
            } else {
                try { Thread.sleep(1000); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
        print("Синхронизация завершена");

        // предметы кончились
        askedSubjects.clear();
        askedTeachers.clear();
        droppedLesson.clear();

        print("Начало обмена");
        iterationExchange();
    }

    void iterationExchange() throws IOException {
        var nextSub = getFirstRemainingLessonForTeacher();
        if (nextSub.isPresent()) {
            var available = getAvailableForTeacherWithoutDropped(true);
            if (!available.isEmpty()) {

                var obj = new ArrayList<String>(3);
                obj.add(available.get(0).getKey().toString());
                obj.add(available.get(0).getValue().toString());
                var body = new MyMessage(obj);
                body.subject = nextSub.get();

                // какая группа
                var mes = new ACLMessage();
                mes.addReceiver(getNextTeacher(nextSub.get().name));
                mes.setConversationId("available");
                mes.setContentObject(body);
                this.myAgent.send(mes);
            } else {
                print("Обмен не продолжился, свободных занятий нет");

                // todo на след этап
                setReady(true);
            }
        } else {
            print("Обмен не продолжился, предметы все заполнены");
            setReady(true);
        }
    }

    AID getNextTeacher(String subject) {
        var teachers = DFUtilities.searchService(this.myAgent, "teacher_[" + subject + "]");
        for (AID teacher : teachers) {
            if (!askedTeachers.contains(teacher.getName())) {
                return teacher;
            }
        }
        return null;
    }

    Optional<Lesson> getFirstRemainingLessonForTeacher() {
        return model.getRemainingLessonsForTeacher().stream()
                .filter(lesson -> !askedSubjects.contains(lesson.name))
                .findFirst();
    }

    SimpleEntry<Integer, Integer> getFirstIntersection(ArrayList<SimpleEntry<Integer, Integer>> availableInfo) {
        return getFirstIntersection(availableInfo, false);
    }

    SimpleEntry<Integer, Integer> getFirstIntersection(ArrayList<SimpleEntry<Integer, Integer>> availableInfo, boolean withDropped) {
        var myAvailable = withDropped
                ? getAvailableForTeacherWithoutDropped()
                : model.getAvailableForTeacher();

        for (var myAval : myAvailable) {
            var isContain = availableInfo.stream().anyMatch(teacherAval -> {
                return Objects.equals(myAval.getKey(), teacherAval.getKey())
                        && Objects.equals(myAval.getValue(), teacherAval.getValue());
            });
            if (isContain) {
                return myAval;
            }
        }
        return null;
    }

    ArrayList<SimpleEntry<Integer, Integer>> getAvailableForTeacherWithoutDropped() {
        return getAvailableForTeacherWithoutDropped(false);
    }

    ArrayList<SimpleEntry<Integer, Integer>> getAvailableForTeacherWithoutDropped(boolean force) {
        var myAvailable = model.getAvailableForTeacher(force);
        return new ArrayList((Collection) myAvailable.stream().filter(myAval ->
                droppedLesson.stream().noneMatch(dropped -> {
                    return Objects.equals(myAval.getKey(), dropped.getKey())
                            && Objects.equals(myAval.getValue(), dropped.getValue());
                })
        ));
    }

    boolean askNextTeacher(String subject) throws IOException {
        return askNextTeacher(subject, false);
    }

    boolean askNextTeacher(String subject, boolean force) throws IOException {
        var next = getNextTeacher(subject);
        if (next != null) {
            // спрашиваем следующего
            var nextMes = new ACLMessage();
            nextMes.addReceiver(next);
            nextMes.setContentObject(new MyMessage());
            nextMes.setConversationId(force ? "availableForce" : "available");
            this.myAgent.send(nextMes);

            return true;
        }
        return false;
    }

    boolean askNextSubject(String prev) throws IOException {
        return askNextSubject(prev, false);
    }

    boolean askNextSubject(String prev, boolean force) throws IOException {
        if (prev != null) {
            // помечаем предмет как опрошенный
            askedSubjects.add(prev);
        } else {
            askedSubjects.clear();
        }

        // очищаем временные значения
        askedTeachers.clear();

        var subject = getFirstRemainingLessonForTeacher();
        if (subject.isPresent()) {
            return askNextTeacher(subject.get().name, force);
        }
        return false;
    }

    // флаг готовности средствами желтых страниц
    void setReady(boolean isReady) {
        ((StudentGroupAgent)myAgent).setReady(isReady);
    }

    void setExchangeReady(boolean isReady) {
        ((StudentGroupAgent)myAgent).setExchangeReady(isReady);
    }

    @Override
    public boolean done() {
        return this.step == 0;
    }
}