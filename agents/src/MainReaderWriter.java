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
}
