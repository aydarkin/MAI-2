import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.AbstractMap;
import java.util.ArrayList;



public class Teacher {
    protected String name;
    protected ArrayList<String> subjects;
    protected ArrayList<ArrayList<Long>> busy;
    protected ArrayList<ArrayList<OccupationItem>> timeTable;

    public Teacher(String name, ArrayList<String> subjects, ArrayList<ArrayList<Long>> busy) {
        this.name = name;
        this.subjects = subjects;
        this.busy = busy;

        timeTable = TimeTable.create();
    }

    public boolean canTeach(String subject) {
        return subjects.contains(subject);
    }

    /**
     * Дни от 0!!!
     */
    public boolean isBusy(int day, int lesson) {
        return this.busy.get(day).contains((long)lesson) || timeTable.get(day).get(lesson).group != null;
    }

    public boolean hasAuditorium(int day, int lesson) {
        return timeTable.get(day).get(lesson).auditorium != null;
    }

    public void setAuditorium(int day, int lesson, String auditorium) {
        timeTable.get(day).get(lesson).auditorium = auditorium;
    }

    public void setGroup(int day, int lesson, String group) {
        timeTable.get(day).get(lesson).group = group;
    }

    public void forceNotBusy(int day, int lesson) {
        this.busy.get(day).remove(
            this.busy.get(day).indexOf(lesson)
        );
    }

    public String whatGroup(int day, int lesson) {
        return timeTable.get(day).get(lesson).group;
    }

    public ArrayList<AbstractMap.SimpleEntry<Integer, Integer>> getAvailable() {
        return getAvailable(false);
    }

    public ArrayList<AbstractMap.SimpleEntry<Integer, Integer>> getAvailable(boolean force) {
        var items = new ArrayList<AbstractMap.SimpleEntry<Integer, Integer>>();

        if (force) {
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 8; j++) {
                    if (timeTable.get(i).get(j).group == null) {
                        items.add(new AbstractMap.SimpleEntry<Integer, Integer>(i, j));
                    }
                }
            }
        } else {
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 4; j++) {
                    if (!isBusy(i,j)) {
                        items.add(new AbstractMap.SimpleEntry<Integer, Integer>(i, j));
                    }
                }
            }
            for (int i = 0; i < 6; i++) {
                for (int j = 4; j < 8; j++) {
                    if (!isBusy(i,j)) {
                        items.add(new AbstractMap.SimpleEntry<Integer, Integer>(i, j));
                    }
                }
            }
        }

        return items;
    }

    public boolean isReady() {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                if (!this.isBusy(i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    public JSONArray getResultTimeTable() {
        var result = new JSONArray();
        JSONArray currentDay;
        JSONObject currentLesson;
        OccupationItem item;
        for (int i = 0; i < 6; i++) {
            currentDay = new JSONArray();
            result.add(currentDay);
            for (int j = 0; j < 8; j++) {
                item = timeTable.get(i).get(j);
                //fixme
                //if (item.group != null && item.auditorium != null) {
                if (item.group != null) {
                    currentLesson = new JSONObject();
                    currentLesson.put("group", item.group);
                    currentLesson.put("subject", item.subject);
                    currentLesson.put("type", item.type == LessonType.LECTURE ? "Лекция" : "Практика");
                    currentLesson.put("auditorium", item.auditorium);
                    currentLesson.put("number", j+1);

                    currentDay.add(currentLesson);
                }
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return this.name;
    }
}