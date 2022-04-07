import java.io.Serializable;

/**
 * Универсальное расписание для агентов
 */
public class OccupationItem implements Serializable {
    public String group;
    public String teacher;
    public String subject;
    public long type;
    public String auditorium;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        var item = (OccupationItem) obj;
        return group.equals(item.group)
                && subject.equals(item.subject)
                && type == item.type
                && auditorium.equals(item.auditorium);
    }
}