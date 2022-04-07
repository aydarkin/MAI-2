public class TestReadInputs {

    public static void main(String[] args) {
        var teachers = MainReaderWriter.readTeacher("C:\\Users\\Kartoshechka\\Desktop\\server.json");
        var auditoriums = MainReaderWriter.readAuditoriums("C:\\Users\\Kartoshechka\\Desktop\\server.json");
        var groups = MainReaderWriter.readStudents("C:\\Users\\Kartoshechka\\Desktop\\client.json");

        System.out.println(teachers);
        System.out.println(auditoriums);
        System.out.println(groups);
    }
}
