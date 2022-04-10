import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;

public class MyMessage implements Serializable {
    public Lesson subject;
    public String group;
    public String teacherOrAuditorium;
    public SimpleEntry<Integer, Integer> targetLesson;
    public Serializable payload;

    public MyMessage() {

    }

    public MyMessage(Serializable payload) {
        this.payload = payload;
    }
}
