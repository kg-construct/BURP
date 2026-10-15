package perf.incremental;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

public class IncrementalDataGenerator {

    private static final String[] SPORTS = {
            "Football", "Basketball", "Tennis", "Swimming", "Athletics",
            "Volleyball", "Rugby", "Badminton", "Cricket", "Handball"
    };

    private static final String[] STATUSES_LEGACY = {
            "LEGACY_ENROLLED", "LEGACY_GRADUATED", "LEGACY_SUSPENDED"
    };

    private static final String[] STATUSES_CURRENT = {
            "ACTIVE", "ALUMNI", "INACTIVE"
    };

    private static final String[] GRADES = {"A", "B", "C", "D", "E", "F"};

    public static void generateParentSubjectChangeData(Path targetDir, int numStudents) throws IOException {
        Files.createDirectories(targetDir);

        int numSports = Math.max(10, numStudents / 10);

        // sports.json
        Path sportsFile = targetDir.resolve("sports.json");
        try (BufferedWriter writer = Files.newBufferedWriter(sportsFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
            writer.write("{\n  \"sports\": [\n");
            for (int i = 1; i <= numSports; i++) {
                String sportName = SPORTS[(i - 1) % SPORTS.length] + " " + i;
                writer.write(String.format("    { \"ID\": \"%d\", \"Name\": \"%s\" }%s%n",
                        i, sportName, (i == numSports ? "" : ",")));
            }
            writer.write("  ]\n}\n");
        }

        // students.json
        Path studentsFile = targetDir.resolve("students.json");
        try (BufferedWriter writer = Files.newBufferedWriter(studentsFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
            writer.write("{\n  \"students\": [\n");
            for (int i = 1; i <= numStudents; i++) {
                int sportId = (i % numSports) + 1;
                writer.write(String.format("    { \"ID\": \"%d\", \"Name\": \"Student %d\", \"SportID\": \"%d\" }%s%n",
                        i, i, sportId, (i == numStudents ? "" : ",")));
            }
            writer.write("  ]\n}\n");
        }
    }

    public static void generatePomPredicateChangeData(Path targetDir, int numStudents) throws IOException {
        Files.createDirectories(targetDir);

        int numSports = Math.max(10, numStudents / 10);

        // sports.json
        Path sportsFile = targetDir.resolve("sports.json");
        try (BufferedWriter writer = Files.newBufferedWriter(sportsFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
            writer.write("{\n  \"sports\": [\n");
            for (int i = 1; i <= numSports; i++) {
                String sportName = SPORTS[(i - 1) % SPORTS.length] + " " + i;
                writer.write(String.format("    { \"ID\": \"%d\", \"Name\": \"%s\" }%s%n",
                        i, sportName, (i == numSports ? "" : ",")));
            }
            writer.write("  ]\n}\n");
        }

        // students.json
        Path studentsFile = targetDir.resolve("students.json");
        try (BufferedWriter writer = Files.newBufferedWriter(studentsFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
            writer.write("{\n  \"students\": [\n");
            for (int i = 1; i <= numStudents; i++) {
                int sportId = (i % numSports) + 1;
                String grade = GRADES[(i - 1) % GRADES.length];
                writer.write(String.format("    { \"ID\": \"%d\", \"Name\": \"Student %d\", \"Grade\": \"%s\", \"SportID\": \"%d\" }%s%n",
                        i, i, grade, sportId, (i == numStudents ? "" : ",")));
            }
            writer.write("  ]\n}\n");
        }
    }

    public static void generateLogicalViewData(Path targetDir, int numStudents) throws IOException {
        Files.createDirectories(targetDir);

        Path csvFile = targetDir.resolve("students.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(csvFile, CREATE, TRUNCATE_EXISTING, WRITE)) {
            writer.write("id,name,legacy_status,current_status\n");
            for (int i = 1; i <= numStudents; i++) {
                String legacy = STATUSES_LEGACY[(i - 1) % STATUSES_LEGACY.length];
                String current = STATUSES_CURRENT[(i - 1) % STATUSES_CURRENT.length];
                writer.write(String.format("student_%d,Student_%d,%s,%s%n", i, i, legacy, current));
            }
        }
    }
}
