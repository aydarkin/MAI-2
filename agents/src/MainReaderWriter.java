import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainReaderWriter {
    public static ArrayList<Teacher> readTeacher(String file) {
        var result = new ArrayList<Teacher>();

        var parser = new JSONParser();
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            var jsonObject = (JSONObject)parser.parse(reader);

            var teachers = (JSONArray)jsonObject.get("teachers");

            for(Object o: teachers){
                if (o instanceof JSONObject teacher) {
                    var subjects = new ArrayList<String>();
                    ((JSONArray)teacher.get("subjects")).forEach(subject -> {
                        subjects.add((String)subject);
                    });

                    var busy = new ArrayList<ArrayList<Long>>();
                    ((JSONArray)teacher.get("busy")).forEach(arr -> {
                        busy.add(new ArrayList<Long>());
                        ((JSONArray)arr).forEach(lesson -> {
                            busy.get(busy.size() - 1).add(((Long) lesson) - 1);
                        });
                    });

                    result.add(new Teacher(teacher.get("name").toString(), subjects, busy));
                }
            }

        } catch (IOException e) {

        } catch (ParseException e) {

        }

        return result;
    }

    public static ArrayList<Auditorium> readAuditoriums(String file) {
        var result = new ArrayList<Auditorium>();

        var parser = new JSONParser();
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            var jsonObject = (JSONObject)parser.parse(reader);

            var auditoriums = (JSONArray)jsonObject.get("auditoriums");

            for(Object o: auditoriums){
                if (o instanceof JSONObject auditorium) {
                    result.add(new Auditorium(
                            (String) auditorium.get("name"),
                            (long) auditorium.get("capacity"),
                            (long) auditorium.get("type")
                    ));
                }
            }

        } catch (IOException e) {

        } catch (ParseException e) {

        }

        return result;
    }

    public static ArrayList<StudentGroup> readStudents(String file) {
        var result = new ArrayList<StudentGroup>();

        var parser = new JSONParser();
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            var studentGroups = (JSONArray)parser.parse(reader);

            for(Object o: studentGroups){
                if (o instanceof JSONObject group) {
                    var subjects = new ArrayList<Subject>();
                    ((JSONArray)group.get("subjects")).forEach(subject -> {
                        subjects.add(new Subject(
                                (String) ((JSONObject)subject).get("name"),
                                (long) ((JSONObject)subject).get("lections"),
                                (long) ((JSONObject)subject).get("practics")
                        ));
                    });

                    result.add(new StudentGroup(
                            (String) group.get("name"),
                            (long) group.get("students"),
                            subjects
                    ));

                }
            }

        } catch (IOException e) {

        } catch (ParseException e) {

        }

        return result;
    }

    public static void write(JSONObject obj, String outFile) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            obj.writeJSONString(writer);
        } catch (IOException e) {

        }
    }

    public static void write(JSONArray arr, String outFile) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            arr.writeJSONString(writer);
        } catch (IOException e) {

        }
    }

    public static void writeCsvServer(JSONObject obj, String outFile) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "WINDOWS-1251"))) {
            var result = new StringBuilder();
            JSONObject teacher, lesson, auditorium;
            JSONArray timeTable, day;

            var empties = new ArrayList<String>();
            var counter = 0;

            // преподаватель, День недели, № занятия, Группа, Предмет, Тип, Аудитория,
            var teachers = (JSONArray)obj.get("teacher");
            result.append("Преподаватель;день недели;№ занятия;Группа;Предмет;Тип;Аудитория\n");
            for (int i = 0; i < teachers.size(); i++) {
                counter = 0;
                teacher = (JSONObject)teachers.get(i);
                timeTable = (JSONArray)teacher.get("timetable");
                for (int j = 0; j < timeTable.size(); j++) {
                    day = (JSONArray)timeTable.get(j);
                    for (int k = 0; k < day.size(); k++) {
                        lesson = (JSONObject)day.get(k);
                        result.append(j == 0 && k == 0 ? teacher.get("teacher") : "").append(";");
                        result.append(k == 0 ? DayOfWeek.get(j) : "").append(";");

                        result.append(lesson.get("number")).append(";");
                        result.append(lesson.get("group")).append(";");
                        result.append(lesson.get("subject")).append(";");
                        result.append(lesson.get("type")).append(";");
                        result.append(lesson.get("auditorium")).append("\n");
                        counter++;
                    }
                }

                if (counter == 0) {
                    empties.add((String) teacher.get("teacher"));
                }
            }
            for (int i = 0; i < empties.size(); i++) {
                result.append(i == 0 ? "Не задействованы ;" : ";");
                result.append(empties.get(i)).append("\n");
            }

            empties.clear();

            result.append(";;;;;;\n");
            result.append("Аудитория;день недели;№ занятия;Группа;Предмет;Тип;Преподаватель\n");
            // аудитория, День недели, № занятия, Группа, Предмет, Тип, Преподаватель,
            var auditoriums = (JSONArray)obj.get("auditorium");
            for (int i = 0; i < auditoriums.size(); i++) {
                counter = 0;
                auditorium = (JSONObject)auditoriums.get(i);
                timeTable = (JSONArray)auditorium.get("timetable");
                for (int j = 0; j < timeTable.size(); j++) {
                    day = (JSONArray)timeTable.get(j);
                    for (int k = 0; k < day.size(); k++) {
                        lesson = (JSONObject)day.get(k);
                        result.append(j == 0 && k == 0 ? auditorium.get("auditorium") : "").append(";");
                        result.append(k == 0 ? DayOfWeek.get(j) : "").append(";");

                        result.append(lesson.get("number")).append(";");
                        result.append(lesson.get("group")).append(";");
                        result.append(lesson.get("subject")).append(";");
                        result.append(lesson.get("type")).append(";");
                        result.append(lesson.get("teacher")).append("\n");
                        counter++;
                    }
                }

                if (counter == 0) {
                    empties.add((String) auditorium.get("auditorium"));
                }
            }
            for (int i = 0; i < empties.size(); i++) {
                result.append(i == 0 ? "Не задействованы ;" : ";");
                result.append(empties.get(i)).append("\n");
            }

            writer.write(result.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeCsvClient(JSONArray groups, String outFile) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "WINDOWS-1251"))) {
            var result = new StringBuilder();
            JSONObject group, lesson;
            JSONArray timeTable, day;

            // преподаватель, День недели, № занятия, Группа, Предмет, Тип, Аудитория,
            result.append("Группа;день недели;№ занятия;Преподаватель;Предмет;Тип;Аудитория\n");
            for (int i = 0; i < groups.size(); i++) {
                group = (JSONObject)groups.get(i);
                timeTable = (JSONArray)group.get("timetable");
                for (int j = 0; j < timeTable.size(); j++) {
                    day = (JSONArray)timeTable.get(j);
                    for (int k = 0; k < day.size(); k++) {
                        lesson = (JSONObject)day.get(k);
                        result.append(j == 0 && k == 0 ? group.get("group") : "").append(";");
                        result.append(k == 0 ? DayOfWeek.get(j) : "").append(";");

                        result.append(lesson.get("number")).append(";");
                        result.append(lesson.get("teacher")).append(";");
                        result.append(lesson.get("subject")).append(";");
                        result.append(lesson.get("type")).append(";");
                        result.append(lesson.get("auditorium")).append("\n");
                    }
                }
            }

            writer.write(result.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class DayOfWeek {
    public static String get(int day) {
        var result = "";
        switch (day) {
            case 0 -> result = "Понедельник";
            case 1 -> result = "Вторник";
            case 2 -> result = "Среда";
            case 3 -> result = "Четверг";
            case 4 -> result = "Пятница";
            case 5 -> result = "Суббота";
            case 6 -> result = "Воскресенье";
        }
        return result;
    }
}
