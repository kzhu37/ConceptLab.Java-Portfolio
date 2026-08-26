import java.nio.file.Path;

/** Verifies that the browser's bundled demo is a valid canonical .clab StudySet. */
public final class BrowserSeedSelfTest {
    public static void main(String[] args) throws Exception {
        StudySet set = StudySet.readFromFile(Path.of("demo/newtonian-mechanics.clab"));
        assert "Newtonian Mechanics".equals(set.getTitle()) : "unexpected demo title";
        assert set.getFlashcards().size() == 8 : "demo flashcard count";
        assert set.getPracticeQuestions().size() == 5 : "demo practice count";
        assert set.getUnitTestQuestions().size() == 8 : "demo unit-test count";
        assert set.getResources().size() == 3 : "demo resource count";
        System.out.println("Browser demo StudySet verified.");
    }
}
