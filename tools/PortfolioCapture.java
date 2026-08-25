import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Generates reproducible screenshots for the public portfolio README.
 *
 * <p>The helper writes an isolated demo StudySet under the temporary user-home
 * supplied by CI, launches the real ConceptLab application, drives the UI, and
 * captures the exact screens used in the README. It does not require API keys;
 * generation/grading falls back locally when remote AI is unavailable.</p>
 */
public final class PortfolioCapture {
    private static final String DEMO_TITLE = "Newtonian Mechanics: Forces, Energy & Momentum";
    private static final Path MEDIA_DIR = Paths.get("docs", "media");

    private PortfolioCapture() {}

    public static void main(String[] args) throws Exception {
        Files.createDirectories(MEDIA_DIR);
        createDemoStudySet();
        Main.main(new String[0]);

        JFrame frame = waitForFrame(12_000);
        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(new Dimension(1600, 980));
            frame.setLocation(80, 40);
            frame.toFront();
        });
        pause(900);

        clickButtonLater(frame, "Load Existing Study Set");
        waitForButton(frame, "Load", 8_000);
        clickRightmostButtonLater(frame, "Load");
        waitForLabelContaining(frame, "Best Test: 88%", 8_000);
        pause(900);
        capture(frame, "conceptlab-dashboard.png");

        clickButtonLater(frame, "Create");
        waitForButton(frame, "Generate Study Set", 5_000);
        fillCreateForm(frame);
        pause(700);
        capture(frame, "create-study-set.png");

        clickButtonLater(frame, "Practice");
        waitForButton(frame, "Generate New Quiz", 5_000);
        clickButtonLater(frame, "Generate New Quiz");
        JDialog settings = waitForDialog("Generate New Quiz", 5_000);
        pause(700);
        capture(frame, "practice-settings.png");
        SwingUtilities.invokeLater(settings::dispose);
        pause(500);

        clickButtonLater(frame, "Unit Test");
        waitForQuiz(frame, 20_000);
        answerCurrentQuestion(frame);
        clickButtonLater(frame, "Submit Answer");
        waitForFeedback(frame, 15_000);
        pause(900);
        capture(frame, "answer-feedback.png");

        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println("Portfolio screenshots generated in " + MEDIA_DIR.toAbsolutePath());
    }

    private static void createDemoStudySet() throws Exception {
        List<Flashcard> cards = List.of(
                new Flashcard("Forces", "Newton's second law", "Net force equals mass times acceleration: F = ma."),
                new Flashcard("Forces", "Free-body diagram", "Represent each external force as a vector acting on the object."),
                new Flashcard("Energy", "Kinetic energy", "KE = 1/2 mv^2, so speed has a squared effect on kinetic energy."),
                new Flashcard("Energy", "Work-energy theorem", "Net work equals the change in kinetic energy."),
                new Flashcard("Momentum", "Linear momentum", "Momentum is p = mv and has the same direction as velocity."),
                new Flashcard("Momentum", "Impulse", "Impulse J = FΔt equals the change in momentum."),
                new Flashcard("Momentum", "Conservation of momentum", "Total momentum stays constant when net external impulse is negligible."),
                new Flashcard("Energy", "Mechanical energy", "In an ideal isolated system, KE + gravitational/elastic potential energy is conserved."),
                new Flashcard("Forces", "Friction", "Friction opposes relative motion or the tendency to slide between surfaces."),
                new Flashcard("Forces", "Normal force", "A surface exerts a perpendicular contact force; it is not automatically equal to weight."),
                new Flashcard("Energy", "Power", "Power is the rate of energy transfer: P = W/t."),
                new Flashcard("Momentum", "Elastic collision", "Both total momentum and total kinetic energy are conserved in an ideal elastic collision.")
        );

        List<Question> practice = new ArrayList<>();
        practice.add(mcq("Forces", "A 4 kg cart has a net force of 20 N. What is its acceleration?", new String[]{"4 m/s^2", "5 m/s^2", "16 m/s^2", "80 m/s^2"}, 1, 0.55));
        practice.add(mcq("Energy", "A cart's speed doubles while its mass stays constant. Its kinetic energy becomes...", new String[]{"half as large", "twice as large", "four times as large", "unchanged"}, 2, 0.62));
        practice.add(mcq("Momentum", "Which change gives the same impulse as doubling a constant force while halving its duration?", new String[]{"No change in impulse", "Double the impulse", "Half the impulse", "Four times the impulse"}, 0, 0.68));
        practice.add(new Question("Energy", "Explain why a normal force can do zero work on an object moving horizontally.", "force is perpendicular to displacement", "Work is Fd cos(theta); with a 90-degree angle between normal force and horizontal displacement, the work is zero.", 0.72, false));
        practice.add(new Question("Momentum", "Why is momentum conserved in an isolated collision?", "net external impulse is zero", "Internal collision forces occur in equal-and-opposite pairs, while zero net external impulse leaves total system momentum unchanged.", 0.76, true));
        practice.add(new Question("Forces", "A block accelerates down a ramp. What does that tell you about the forces parallel to the ramp?", "net force down the ramp is nonzero", "Acceleration requires a nonzero net force in the direction of acceleration, so downhill forces exceed uphill forces.", 0.70, false));

        List<Question> unit = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            unit.add(mcq(
                    i % 3 == 0 ? "Forces" : (i % 3 == 1 ? "Energy" : "Momentum"),
                    "Demo unit-test application question " + (i + 1) + ": which reasoning step best matches the mechanics principle?",
                    new String[]{"Identify the system and relevant conservation law", "Ignore vector direction", "Assume every force equals weight", "Use a memorized value without units"},
                    0,
                    0.72 + (i % 4) * 0.04
            ));
        }
        for (int i = 0; i < 6; i++) {
            unit.add(new Question(
                    i % 2 == 0 ? "Energy" : "Momentum",
                    "Demo unit-test short response " + (i + 1) + ": justify the governing mechanics relationship before calculating.",
                    "identify the governing relationship and justify assumptions",
                    "A complete response states the governing law, defines the system, and justifies assumptions before substituting values.",
                    0.78 + (i % 3) * 0.05,
                    true
            ));
        }

        List<ResourceLink> resources = List.of(
                new ResourceLink("Forces", "OpenStax: Newton's Laws", ResourceType.REFERENCE, "https://openstax.org/books/physics/pages/5-introduction"),
                new ResourceLink("Energy", "OpenStax: Work and Kinetic Energy", ResourceType.REFERENCE, "https://openstax.org/books/physics/pages/9-2-work-and-kinetic-energy"),
                new ResourceLink("Momentum", "OpenStax: Linear Momentum", ResourceType.REFERENCE, "https://openstax.org/books/physics/pages/8-introduction"),
                new ResourceLink("Forces", "PhET Forces and Motion", ResourceType.SIMULATION, "https://phet.colorado.edu/en/simulations/forces-and-motion-basics")
        );

        StudySet set = new StudySet(DEMO_TITLE, cards, practice, unit, resources, 88.0);
        Path sets = Paths.get(System.getProperty("user.home"), ".conceptlab", "sets");
        Files.createDirectories(sets);
        set.storeToFile(sets.resolve("newtonian_mechanics_demo.clab"));
    }

    private static Question mcq(String topic, String prompt, String[] choices, int correct, double difficulty) {
        String[] feedback = new String[choices.length];
        for (int i = 0; i < feedback.length; i++) {
            feedback[i] = i == correct
                    ? "Correct. This applies the governing relationship to the given conditions."
                    : "Re-check the governing relationship, units, and what the question is actually asking you to infer.";
        }
        return new Question(topic, prompt, choices, correct, feedback, difficulty, difficulty >= 0.75);
    }

    private static JFrame waitForFrame(long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame f && f.isShowing() && "ConceptLab".equals(f.getTitle())) {
                    return f;
                }
            }
            pause(100);
        }
        throw new IllegalStateException("ConceptLab frame did not appear");
    }

    private static JDialog waitForDialog(String title, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) {
                    return d;
                }
            }
            pause(100);
        }
        throw new IllegalStateException("Dialog did not appear: " + title);
    }

    private static JButton waitForButton(Container root, String text, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            JButton b = findButton(root, text);
            if (b != null && b.isShowing() && b.isEnabled()) {
                return b;
            }
            pause(100);
        }
        throw new IllegalStateException("Button did not appear: " + text);
    }

    private static void clickButtonLater(Container root, String text) throws Exception {
        JButton b = waitForButton(root, text, 8_000);
        SwingUtilities.invokeLater(b::doClick);
        pause(300);
    }

    private static void clickRightmostButtonLater(Container root, String text) throws Exception {
        JButton chosen = null;
        int bestX = Integer.MIN_VALUE;
        for (Component c : allComponents(root)) {
            if (c instanceof JButton b && text.equals(b.getText()) && b.isShowing() && b.isEnabled()) {
                int x = SwingUtilities.convertPoint(b.getParent(), b.getLocation(), root).x;
                if (x > bestX) {
                    bestX = x;
                    chosen = b;
                }
            }
        }
        if (chosen == null) throw new IllegalStateException("No visible button found: " + text);
        JButton target = chosen;
        SwingUtilities.invokeLater(target::doClick);
        pause(350);
    }

    private static JButton findButton(Container root, String text) {
        for (Component c : allComponents(root)) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    private static void waitForLabelContaining(Container root, String text, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Component c : allComponents(root)) {
                if (c instanceof JLabel l && l.isShowing() && l.getText() != null && l.getText().contains(text)) {
                    return;
                }
            }
            pause(100);
        }
        throw new IllegalStateException("Label did not appear containing: " + text);
    }

    private static void fillCreateForm(Container root) throws Exception {
        List<JTextField> fields = new ArrayList<>();
        List<JTextArea> areas = new ArrayList<>();
        for (Component c : allComponents(root)) {
            if (!c.isShowing()) continue;
            if (c instanceof JTextField f) fields.add(f);
            if (c instanceof JTextArea a) areas.add(a);
        }
        SwingUtilities.invokeAndWait(() -> {
            if (!fields.isEmpty()) fields.get(0).setText("Newtonian Mechanics Review");
            if (areas.size() > 0) areas.get(0).setText("A net force changes an object's velocity according to F = ma. Kinetic energy depends on mass and the square of speed. Momentum p = mv is conserved when net external impulse is negligible. Work transfers energy, and impulse changes momentum.");
            if (areas.size() > 1) areas.get(1).setText("Practice applying force, energy, and momentum relationships to unfamiliar multi-step scenarios.");
            if (areas.size() > 2) areas.get(2).setText("Prioritize reasoning, unit analysis, and questions that require choosing the correct physical principle.");
            for (Component c : allComponents(root)) {
                if (c instanceof JSpinner s && c.isShowing()) {
                    try { s.setValue(12); } catch (Exception ignored) {}
                }
            }
        });
    }

    private static void waitForQuiz(Container root, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Component c : allComponents(root)) {
                if (c instanceof JLabel l && l.isShowing() && l.getText() != null && l.getText().contains("Question 1 of")) {
                    return;
                }
            }
            pause(150);
        }
        throw new IllegalStateException("Quiz did not start");
    }

    private static void answerCurrentQuestion(Container root) throws Exception {
        for (Component c : allComponents(root)) {
            if (c instanceof AbstractButton b && c.getClass().getName().endsWith("JRadioButton") && b.isShowing() && b.isEnabled()) {
                SwingUtilities.invokeAndWait(() -> b.setSelected(true));
                return;
            }
        }
        for (Component c : allComponents(root)) {
            if (c instanceof JTextArea area && area.isShowing() && area.isEditable() && area.isEnabled()) {
                SwingUtilities.invokeAndWait(() -> area.setText("The governing relationship must be chosen from the system conditions, then applied with consistent units and justified assumptions."));
                return;
            }
        }
        throw new IllegalStateException("No answer input found");
    }

    private static void waitForFeedback(Container root, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Component c : allComponents(root)) {
                if (c instanceof JLabel l && l.isShowing()) {
                    String t = l.getText();
                    if ("Correct".equals(t) || "Not Correct".equals(t)) return;
                }
            }
            pause(120);
        }
        throw new IllegalStateException("Answer feedback did not appear");
    }

    private static List<Component> allComponents(Container root) {
        List<Component> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, out);
        }
    }

    private static void capture(JFrame frame, String fileName) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.toFront();
            frame.repaint();
        });
        pause(350);
        Rectangle bounds = frame.getBounds();
        BufferedImage image = new Robot().createScreenCapture(bounds);
        ImageIO.write(image, "png", MEDIA_DIR.resolve(fileName).toFile());
        System.out.println("Captured " + fileName + " " + image.getWidth() + "x" + image.getHeight());
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
