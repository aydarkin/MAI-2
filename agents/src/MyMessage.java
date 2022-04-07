import java.io.Serializable;

public class MyMessage implements Serializable {
    public Lesson subject;
    public String group;
    public Serializable payload;

    public MyMessage() {

    }

    public MyMessage(Serializable payload) {
        this.payload = payload;
    }
}
