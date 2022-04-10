import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;

public class StudentGroup {
    protected String name;
    protected long students;
    protected ArrayList<Subject> subjects;
    protected ArrayList<ArrayList<OccupationItem>> timeTable;

    protected int allLessons;

    public StudentGroup(String name, long students, ArrayList<Subject> subjects) {
        this.name = name;
        this.students = students;
        this.subjects = subjects;

        timeTable = TimeTable.create();
    }

    /**
     * Дни от 0!!!
     */
    public boolean isBusy(int day, int lesson) {
        return timeTable.get(day).get(lesson).teacher != null;
    }

    public boolean hasAuditorium(int day, int lesson) {
        return timeTable.get(day).get(lesson).auditorium != null;
    }

    public void setTeacher(int day, int lesson, String teacher) {
        timeTable.get(day).get(lesson).teacher = teacher;
    }

    public void setAuditorium(int day, int lesson, String auditorium) {
        timeTable.get(day).get(lesson).auditorium = auditorium;
    }

    public ArrayList<SimpleEntry<Integer, Integer>> getAvailableForTeacher() {
        return getAvailableForTeacher(false);
    }

    public ArrayList<SimpleEntry<Integer, Integer>> getAvailableForTeacher(boolean force) {
        var items = new ArrayList<SimpleEntry<Integer, Integer>>();

        // если нужны 5-8 пары
        if (force) {
            for (int j = 4; j < 8; j++) {
                for (int i = 0; i < 6; i++) {
                    if (timeTable.get(i).get(j).teacher == null) {
                        items.add(new SimpleEntry<Integer, Integer>(i, j));
                    }
                }
            }
        }

        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 6; i++) {
                if (timeTable.get(i).get(j).teacher == null) {
                    items.add(new SimpleEntry<Integer, Integer>(i, j));
                }
            }
        }

        return items;
    }

    public ArrayList<SimpleEntry<Integer, Integer>> getAvailableForAuditorium(boolean force) {
        var items = new ArrayList<SimpleEntry<Integer, Integer>>();

        // если нужны 5-8 пары
        if (force) {
            for (int j = 4; j < 8; j++) {
                for (int i = 0; i < 6; i++) {
                    if (timeTable.get(i).get(j).auditorium == null && timeTable.get(i).get(j).teacher != null) {
                        items.add(new SimpleEntry<Integer, Integer>(i, j));
                    }
                }
            }
        }

        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 6; i++) {
                if (timeTable.get(i).get(j).auditorium == null && timeTable.get(i).get(j).teacher != null) {
                    items.add(new SimpleEntry<Integer, Integer>(i, j));
                }
            }
        }

        return items;
    }

    // оставшиеся предметы для поиска преподавателя
    public ArrayList<Lesson> getRemainingLessonsForTeacher() {
        var result = new ArrayList<Lesson>();
        for (var sub : subjects) {
            int lections = 0;
            int practics = 0;
            OccupationItem item;
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 8; j++) {
                    item = timeTable.get(i).get(j);
                    if (item.subject != null && item.subject.equals(sub.name) && item.teacher != null) {
                        if (item.type == LessonType.PRACTICE)
                            practics++;
                        else
                            lections++;
                    }
                }
            }

            for (int i = 0; i < (sub.lections - lections); i++) {
                result.add(new Lesson(sub.name, LessonType.LECTURE));
            }
            for (int i = 0; i < (sub.practics - practics); i++) {
                result.add(new Lesson(sub.name, LessonType.PRACTICE));
            }
        }
        return result;
    }

    // оставшиеся предметы для поиска аудитории
    public ArrayList<Lesson> getRemainingLessonsForAuditorium() {
        var result = new ArrayList<Lesson>();
        for (var sub : subjects) {
            int lections = 0;
            int practics = 0;
            OccupationItem item;
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 8; j++) {
                    item = timeTable.get(i).get(j);
                    if (item.subject != null && item.subject.equals(sub.name) && item.teacher != null && item.auditorium != null) {
                        if (item.type == LessonType.PRACTICE)
                            practics++;
                        else
                            lections++;
                    }
                }
            }

            for (int i = 0; i < (sub.lections - lections); i++) {
                result.add(new Lesson(sub.name, LessonType.LECTURE));
            }
            for (int i = 0; i < (sub.practics - practics); i++) {
                result.add(new Lesson(sub.name, LessonType.PRACTICE));
            }
        }

        return result;
    }

    public boolean isReadyLessons() {
        for (var sub : subjects) {
            int lections = 0;
            int practics = 0;
            OccupationItem item;
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 8; j++) {
                    item = timeTable.get(i).get(j);
                    if (item.subject != null && item.subject.equals(sub.name) && item.teacher != null) {
                        if (item.type == LessonType.PRACTICE)
                            practics++;
                        else
                            lections++;
                    }
                }
            }
            if (sub.lections != lections || sub.practics != practics)
                return false;
        }
        return true;
    }

    public boolean isReady() {
        if (!isReadyLessons())
            return false;

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                if (timeTable.get(i).get(j).auditorium == null) {
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
                if (item.teacher != null) {
                //if (item.teacher != null && item.auditorium != null) {
                    currentLesson = new JSONObject();
                    currentLesson.put("subject", item.subject);
                    currentLesson.put("type", item.type == LessonType.LECTURE ? "Лекция" : "Практика");
                    currentLesson.put("teacher", item.teacher);
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
