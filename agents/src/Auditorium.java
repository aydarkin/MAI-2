import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.AbstractMap;
import java.util.ArrayList;

public class Auditorium {
    public String name;
    public long capacity;
    public long type;
    protected ArrayList<ArrayList<OccupationItem>> timeTable;

    public Auditorium(String name, long capacity, long type) {
        this.name = name;
        this.capacity = capacity;
        this.type = type;

        timeTable = TimeTable.create();
    }

    public boolean isBusy(int day, int lesson) {
        return timeTable.get(day).get(lesson).group != null;
    }

    public String whatGroup(int day, int lesson) {
        return timeTable.get(day).get(lesson).group;
    }

    public ArrayList<AbstractMap.SimpleEntry<Integer, Integer>> getAvailable() {
        var items = new ArrayList<AbstractMap.SimpleEntry<Integer, Integer>>();
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

        return items;
    }

    public boolean canOccupation(long type, long capacity) {
        // лекционный кабинет подходит и для практик
        return (this.type == LessonType.LECTURE || this.type == type)
                && this.capacity >= capacity;
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
                if (item.teacher != null && item.group != null) {
                    currentLesson = new JSONObject();
                    currentLesson.put("group", item.group);
                    currentLesson.put("subject", item.subject);
                    currentLesson.put("type", item.type == LessonType.LECTURE ? "Лекция" : "Практика");
                    currentLesson.put("teacher", item.teacher);
                    currentLesson.put("number", j+1);

                    currentDay.add(currentLesson);
                }
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return this.name + "("
                + (this.type == LessonType.LECTURE ? "Л" : "П")
                + ", " + this.capacity + ")";
    }
}
