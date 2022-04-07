import java.util.ArrayList;

public class TimeTable {
    public static ArrayList<ArrayList<OccupationItem>> create() {
        var items = new ArrayList<ArrayList<OccupationItem>>();
        for (int i = 0; i < 6; i++) {
            items.add(new ArrayList<OccupationItem>());
            for (int j = 0; j < 8; j++) {
                items.get(i).add(new OccupationItem());
            }
        }

        return items;
    }
}
