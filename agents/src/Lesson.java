import java.io.Serializable;

public class Lesson implements Serializable {
    public String name;
    public int type;

    public Lesson(String name, int type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return this.name + " (" + (this.type == LessonType.LECTURE ? "лекция" : "практика") + ")";
    }
}
