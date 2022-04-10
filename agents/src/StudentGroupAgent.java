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

    void setExchangeReady(boolean isReady, String postfix) {
        if (isReady) {
            statuses.add("groupExchangeReady" + postfix);
        } else {
            statuses.remove("groupExchangeReady" + postfix);
        }
        setStatuses();
    }

    void setExchangeDone(boolean isReady) {
        if (isReady) {
            statuses.add("groupExchangeDone");
        } else {
            statuses.remove("groupExchangeDone");
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

    ArrayList<String> askedTeachersAuditoriums = new ArrayList<String>();
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
        System.out.println(this.myAgent.getLocalName() + "[" + this.step + "]: " + message);
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
                        availableHandler(mes, false);
                    }
                    case "proposal" -> {
                        proposalHandler(mes, false);
                    }
                    case "availableForce" -> {
                        availableHandler(mes, true);
                    }
                    case "proposalForce" -> {
                        proposalHandler(mes, true);
                    }
                    case "exchange" -> {
                        var myMsg = (MyMessage) mes.getContentObject();

                        switch (mes.getPerformative()) {
                            case ACLMessage.REQUEST -> {
                                var obj = (ArrayList<String>) myMsg.payload;
                                var day = Integer.parseInt(obj.get(0));
                                var lesson = Integer.parseInt(obj.get(1));

                                // получен запрос на обмен
                                var teacherOrAuditorium = step <= 3
                                        ? model.timeTable.get(day).get(lesson).teacher
                                        : model.timeTable.get(day).get(lesson).auditorium;
                                if (teacherOrAuditorium != null) {
                                    // спрашиваем расписание
                                    var msg = new ACLMessage();

                                    msg.addReceiver(step <= 3
                                            ? findTeacher(teacherOrAuditorium)
                                            : findAuditorium(teacherOrAuditorium));

                                    msg.setConversationId("available");
                                    msg.setContentObject(myMsg);
                                    this.myAgent.send(msg);
                                } else {
                                    // отвечаем отказом на обмен
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
                                var day = myMsg.targetLesson.getKey();
                                var lesson = myMsg.targetLesson.getValue();

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
                            var groupName = obj.get(2);

                            myMsg.targetLesson = new SimpleEntry<>(day, lesson);

                            var groupAgent = findGroup(groupName);
                            if (groupAgent == null) {
                                print("Не найден " + groupName + ", данные: " + obj);
                            }
                            print("Шлем запрос на обмен группе " + groupName + " -> " + groupAgent.getName());

                            var msg = new ACLMessage();
                            msg.addReceiver(groupAgent);
                            msg.setConversationId("exchange");
                            msg.setPerformative(ACLMessage.REQUEST);
                            msg.setContentObject(myMsg);
                            this.myAgent.send(msg);
                        } else {
                            // занят
                            print((step <= 3 ? "Преподаватель" : "Аудитория") + " недоступен в пару " + day + "-" + lesson);
                            droppedLesson.add(new SimpleEntry<Integer, Integer>(day, lesson));
                            iterationExchange();
                        }
                    }
                    case "transferLesson" -> {
                        var myMsg = (MyMessage) mes.getContentObject();

                        var msg = new ACLMessage();
                        msg.addReceiver(findGroup(myMsg.group));
                        msg.setConversationId("exchange");
                        if (mes.getPerformative() == ACLMessage.AGREE) {
                            msg.setPerformative(ACLMessage.AGREE);
                            print((step <= 3 ? "Преподаватель" : "Аудитория") + " разрешил перенос");

                            var obj = (ArrayList<String>) myMsg.payload;
                            var group = obj.get(0);
                            var day = Integer.parseInt(obj.get(1));
                            var lesson = Integer.parseInt(obj.get(2));
                            var newDay = Integer.parseInt(obj.get(3));
                            var newLesson = Integer.parseInt(obj.get(4));

                            if (step <= 3) {
                                model.timeTable.get(day).get(lesson).teacher = null;
                                model.timeTable.get(newDay).get(newLesson).teacher = myMsg.teacherOrAuditorium;
                            } else {
                                model.timeTable.get(day).get(lesson).auditorium = null;
                                model.timeTable.get(newDay).get(newLesson).auditorium = myMsg.teacherOrAuditorium;
                            }
                        } else {
                            print((step <= 3 ? "Преподаватель" : "Аудитория") + " не разрешил перенос, время успели занять");
                            msg.setPerformative(ACLMessage.CANCEL);
                        }
                        this.myAgent.send(msg);
                    }
                    case "SYNC_GROUP" -> {
                        print("!!!Синхронизировано через менеджер!!!");
                        // запуск принудительного заполнения
                        if (step == 2 || step == 5) {
                            startForce();
                        } else if (step == 3){
                            startAuditorium();
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
            if (group.getName().split("@")[0].equals(name)) {
                return group;
            }
        }
        return null;
    }

    AID findTeacher(String name) {
        var teachers = DFUtilities.searchService(myAgent, "teacher");
        for (AID teacher : teachers) {
            if (teacher.getName().split("@")[0].equals(name)) {
                return teacher;
            }
        }
        return null;
    }

    AID findAuditorium(String name) {
        var auditoriums = DFUtilities.searchService(myAgent, "auditorium");
        for (AID auditorium : auditoriums) {
            if (auditorium.getName().split("@")[0].equals(name)) {
                return auditorium;
            }
        }
        return null;
    }

    ArrayList<String> remainingGroups() {
        var groups = DFUtilities.searchService(myAgent, "group");
        var readies = DFUtilities.searchService(myAgent, "groupReady");

        var result = new ArrayList<String>();
        for (AID group : groups) {
            if (Arrays.stream(readies).noneMatch(ready -> group.getName().equals(ready.getName()))) {
                result.add(group.getName());
            }
        }
        return result;
    }

    void availableHandler(ACLMessage mes, boolean force) throws UnreadableException, IOException  {
        var myMsg = (MyMessage) mes.getContentObject();
        var availableInfo = (ArrayList<SimpleEntry<Integer, Integer>>) myMsg.payload;
        var selected = getFirstIntersection(availableInfo, step == 2 || step == 5, force);
        var subject = getFirstRemainingLesson();
        if (selected != null && subject.isPresent()) {
            if ((this.step == 2 || step == 5) && myMsg.group != null) {
                // ответ для переноса по просьбе другой группы
                print((step <= 3 ? "Преподаватель" : "Аудитория") + " ответил для переноса пары, есть пересечения");
                var obj = new ArrayList<String>(5);
                obj.add(model.name);
                obj.add(myMsg.targetLesson.getKey().toString()); // откуда
                obj.add(myMsg.targetLesson.getValue().toString());
                obj.add(selected.getKey().toString()); // куда
                obj.add(selected.getValue().toString());
                myMsg.payload = obj;

                var msg = mes.createReply();
                msg.setConversationId("transferLesson");
                msg.setContentObject(myMsg);
                this.myAgent.send(msg);
            } else {
                print("Есть пересечение " + selected);
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
            }
        } else if (subject.isPresent()) {
            if (this.step == 1 || this.step == 3 || this.step == 4 || this.step == 6) {
                print("Нет пересечения. Спрашиваем следующего " + (step <= 3 ? "преподавателя" : "аудиторию"));
                askedTeachersAuditoriums.add(mes.getSender().getName());
                var asked = step <= 3
                        ? askNextTeacher(subject.get(), force)
                        : askNextAuditorium(subject.get(), force);
                if (!asked) {
                    print((step <= 3 ? "Преподаватели" : "Аудитории") + " кончились. Спрашиваем следующий предмет");
                    var askedSubject = askNextSubject(subject.get(), force);
                    if (!askedSubject) {
                        print("Первичное распределение завершено");
                        print("Мне осталось: " + (step <= 3
                                ? model.getRemainingLessonsForTeacher()
                                : model.getRemainingLessonsForAuditorium()));

                        switch (step) {
                            case 1, 4 -> startExchange();
                            case 2, 3, 5 -> startSync();
                            case 6 -> setReady(true);
                        }
                    }
                }
            } else if (this.step == 2 || this.step == 5) {
                if (myMsg.group != null) {
                    // Г2 спросил П2
                    print("Преподаватель ответил для переноса пары, пересечений нет");
                    var msg = new ACLMessage();

                    msg.addReceiver(findGroup(myMsg.group));
                    msg.setConversationId("exchange");
                    msg.setPerformative(ACLMessage.CANCEL);
                    msg.setContentObject(myMsg);
                    this.myAgent.send(msg);
                } else {
                    // Г1 спросил П1
                    askWhatGroup(mes, myMsg);
                }
            }
        } else {
            if ((step == 2 || step == 5) && myMsg.group != null) {
                // Г2 спросил П2
                print("Некуда переносить");
                var msg = new ACLMessage();

                msg.addReceiver(findGroup(myMsg.group));
                msg.setConversationId("exchange");
                msg.setPerformative(ACLMessage.CANCEL);
                msg.setContentObject(myMsg);
                this.myAgent.send(msg);
            } else {
                // нет больше предметов
                print("Кончились предметы");
                switch (step) {
                    case 1, 4 -> startExchange();
                    case 2, 3, 5 -> startSync();
                    case 6 -> setReady(true);
                }
            }
        }
    }

    void startSync() {
        print("Синхронизация через менеджер");

        var mes = new ACLMessage();
        mes.addReceiver(client);
        mes.setContent(model.name);
        mes.setConversationId("SYNC_GROUP");
        mes.setPerformative(ACLMessage.AGREE);
        myAgent.send(mes);
    }

    void startForce() throws IOException {
        if (step <= 3) {
            this.step = 3;
        } else {
            this.step = 6;
        }

        // предметы кончились
        askedSubjects.clear();
        askedTeachersAuditoriums.clear();
        droppedLesson.clear();

        print("!!!Начало принудительного заполнения!!!");
        var askedSubject = askNextSubject(null, true);
        if (!askedSubject) {
            print("Принудительное заполнение не понадобилось");
            print("Мне осталось: " + (step <= 3
                    ? model.getRemainingLessonsForTeacher()
                    : model.getRemainingLessonsForAuditorium()));
            if (step <= 3) {
                startSync();
            } else {
                setReady(true);
            }

        }
    }

    void startAuditorium() throws IOException {
        this.step = 4;
        askNextSubject(null);
        var askedSubject = askNextSubject(null);
        if (!askedSubject) {
            print("Первичное распределение аудиторий завершено");
            startExchange();
        }
    }

    void askWhatGroup(ACLMessage mes, MyMessage myMsg) throws IOException {
        var myAval = getAvailableWithoutDropped();
        if (!myAval.isEmpty()) {
            // шлем чья группа, получим ответ в контесте
            var obj = new ArrayList<String>(2);
            obj.add(myAval.get(0).getKey().toString());
            obj.add(myAval.get(0).getValue().toString());
            myMsg.payload = obj;
            myMsg.group = model.name;

            var msg = mes.createReply();
            msg.setConversationId("whatGroup");
            msg.setContentObject(myMsg);
            this.myAgent.send(msg);
        } else {
            // нет больше предметов
            print("Кончились пары для обмена");
            startSync();
        }
    }

    void proposalHandler(ACLMessage mes, boolean force) throws IOException, UnreadableException {
        var performative = mes.getPerformative();
        var myMsg = (MyMessage) mes.getContentObject();
        if (performative == ACLMessage.AGREE) {
            // преподаватель установлен
            // читаем группу, день, урок
            var obj = (ArrayList<String>) myMsg.payload;
            var teacherOrAuditorium = obj.get(0);
            var day = Integer.parseInt(obj.get(1));
            var lesson = Integer.parseInt(obj.get(2));
            var subject = obj.get(3);
            var type = Integer.parseInt(obj.get(4));

            print("Добавлен " + (step <= 3 ? "преподаватель " : "аудитория ")  + teacherOrAuditorium + " на " + day +" " + lesson);

            var occupation = model.timeTable.get(day).get(lesson);
            if (step <= 3) {
                occupation.teacher = teacherOrAuditorium;
            } else {
                occupation.auditorium = teacherOrAuditorium;
                sendAccept(occupation, day, lesson);
            }


            model.timeTable.get(day).get(lesson).subject = subject;
            model.timeTable.get(day).get(lesson).type = type;

            if (step == 1 || this.step == 3 || this.step == 4 || this.step == 6) {
                // спрашиваем дальше
                var askedSubject = askNextSubject(null, force);
                // иначе
                if (!askedSubject) {
                    print("Первичное распределение завершено после очередного заполнения");
                    print("Мне осталось: " + (step <= 3
                            ? model.getRemainingLessonsForTeacher()
                            : model.getRemainingLessonsForAuditorium()));

                    switch (step) {
                        case 1, 4 -> startExchange();
                        case 3 -> startSync();
                        case 6 -> setReady(true);

                        //case 3 -> setReady(true);
                    }
                }
            } else if (step == 2 || this.step == 5) {
                iterationExchange();
            }
        } else {
            if (step == 1 || this.step == 3 || this.step == 4 || this.step == 6) {
                // повторяем предложение
                availableHandler(mes, force);
            } else if (step == 2 || this.step == 5) {
                // спрашиваем кто
                askWhatGroup(mes, myMsg);
            }
        }
    }

    private void sendAccept(OccupationItem occupation, int day, int lesson) throws IOException {
        var teacher = findTeacher(occupation.teacher);
        var auditorium = findAuditorium(occupation.auditorium);
        var teacherName = occupation.teacher;

        var myMsg = new MyMessage();
        var obj = new ArrayList<String>(3);
        obj.add(occupation.auditorium);
        obj.add(Integer.toString(day));
        obj.add(Integer.toString(lesson));
        myMsg.payload = obj;

        // преподавателю
        var mes = new ACLMessage();
        mes.addReceiver(teacher);
        mes.setConversationId("selectAuditorium");
        mes.setContentObject(myMsg);
        this.myAgent.send(mes);

        // аудитории
        ((ArrayList<String>)myMsg.payload).set(0, teacherName);
        mes.clearAllReceiver();
        mes.addReceiver(auditorium);
        mes.setConversationId("selectTeacher");
        mes.setContentObject(myMsg);
        this.myAgent.send(mes);
    }

    void startExchange() throws IOException {
        if (this.step == 1) {
            this.step = 2;
            setExchangeReady(true);
        } else {
            this.step = 5;
            setExchangeReady(true, "2");
        }

        print("Синхронизация");
        // синхронизация
        var flag = true;
        while (flag) {
            // разные типы, т.к. перерегистрация работает через снятие регистрации
            var readyGroups = DFUtilities.searchService(myAgent, "groupExchangeReady" + (step == 2 ? "" : "2"));
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
        askedTeachersAuditoriums.clear();
        droppedLesson.clear();

        print("Начало обмена");
        iterationExchange();
    }

    void iterationExchange() throws IOException {
        var nextSub = getFirstRemainingLesson();
        if (nextSub.isPresent()) {
            var available = getAvailableWithoutDropped(true);
            if (!available.isEmpty()) {

                var obj = new ArrayList<String>(3);
                obj.add(available.get(0).getKey().toString());
                obj.add(available.get(0).getValue().toString());
                var body = new MyMessage(obj);
                body.subject = nextSub.get();

                // какая группа
                var mes = new ACLMessage();
                mes.addReceiver(step <= 3
                        ? getNextTeacher(nextSub.get())
                        : getNextAuditorium(nextSub.get()));
                mes.setConversationId("available");
                mes.setContentObject(body);
                this.myAgent.send(mes);
            } else {
                print("Обмен не продолжился, свободных занятий нет");
                startSync();
            }
        } else {
            print("Обмен не продолжился, предметы все заполнены");
            startSync();
        }
    }

    AID getNextTeacher(Lesson subject) {
        var teachers = DFUtilities.searchService(this.myAgent, "teacher_[" + subject.name + "]");
        for (AID teacher : teachers) {
            if (!askedTeachersAuditoriums.contains(teacher.getName())) {
                return teacher;
            }
        }
        return null;
    }

    AID getNextAuditorium(Lesson subject) {
        var auditoriums = DFUtilities.searchService(this.myAgent,
                "auditorium" + (subject.type == LessonType.LECTURE ? "Lecture" : "Practice"));
        for (AID auditorium : auditoriums) {
            if (!askedTeachersAuditoriums.contains(auditorium.getName())) {
                return auditorium;
            }
        }
        return null;
    }

    Optional<Lesson> getFirstRemainingLesson() {
        var remaining = step <= 3
                ? model.getRemainingLessonsForTeacher()
                : model.getRemainingLessonsForAuditorium();

        return remaining.stream()
                .filter(lesson -> !askedSubjects.contains(lesson.name))
                .findFirst();
    }

    SimpleEntry<Integer, Integer> getFirstIntersection(ArrayList<SimpleEntry<Integer, Integer>> availableInfo, boolean withoutDropped, boolean force) {
        var myAvailable = withoutDropped
                ? getAvailableWithoutDropped()
                : (step <= 3
                    ? model.getAvailableForTeacher(force)
                    : model.getAvailableForAuditorium(force));

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

    ArrayList<SimpleEntry<Integer, Integer>> getAvailableWithoutDropped() {
        return getAvailableWithoutDropped(false);
    }

    ArrayList<SimpleEntry<Integer, Integer>> getAvailableWithoutDropped(boolean force) {
        var myAvailable = step <= 3
                ? model.getAvailableForTeacher(force)
                : model.getAvailableForAuditorium(force);

        return new ArrayList(myAvailable.stream().filter(myAval ->
                droppedLesson.stream().noneMatch(dropped -> {
                    return Objects.equals(myAval.getKey(), dropped.getKey())
                            && Objects.equals(myAval.getValue(), dropped.getValue());
                })
        ).toList());
    }

    boolean askNextTeacherAuditorium(AID next, boolean force)  throws IOException {
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

    boolean askNextTeacher(Lesson subject, boolean force) throws IOException {
        return askNextTeacherAuditorium(getNextTeacher(subject), force);
    }

    boolean askNextAuditorium(Lesson subject, boolean force) throws IOException {
        return askNextTeacherAuditorium(getNextAuditorium(subject), force);
    }

    boolean askNextSubject(Lesson prev) throws IOException {
        return askNextSubject(prev, false);
    }

    boolean askNextSubject(Lesson prev, boolean force) throws IOException {
        if (prev != null) {
            // помечаем предмет как опрошенный
            askedSubjects.add(prev.name);
        } else {
            askedSubjects.clear();
        }

        // очищаем временные значения
        askedTeachersAuditoriums.clear();

        var subject = getFirstRemainingLesson();
        if (subject.isPresent()) {
            if (step <= 3) {
                return askNextTeacher(subject.get(), force);
            } else {
                return askNextAuditorium(subject.get(), force);
            }
        }
        return false;
    }

    // флаг готовности средствами желтых страниц
    void setReady(boolean isReady) {
        //((StudentGroupAgent)myAgent).setReady(isReady);

        var mes = new ACLMessage();
        mes.addReceiver(client);
        mes.setContent(model.name);
        mes.setConversationId("READY_GROUP");
        if (isReady) {
            print("Мне осталось: " + (step <= 3
                    ? model.getRemainingLessonsForTeacher()
                    : model.getRemainingLessonsForAuditorium()));

            mes.setPerformative(ACLMessage.AGREE);
        } else {
            mes.setPerformative(ACLMessage.CANCEL);
        }
        myAgent.send(mes);
    }

    void setExchangeReady(boolean isReady) {
        setExchangeReady(isReady, "");
    }

    void setExchangeReady(boolean isReady, String postfix) {
        ((StudentGroupAgent)myAgent).setExchangeReady(isReady, postfix);
    }

    void setExchangeDone(boolean isReady) {
        ((StudentGroupAgent)myAgent).setExchangeDone(isReady);
    }

    @Override
    public boolean done() {
        return this.step == 0;
    }
}