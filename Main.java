
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.WindowConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * Main desktop application class for ConceptLab.
 *
 * <p>
 * This class owns: screen construction and navigation, in-memory UI state,
 * study content generation, quiz lifecycle handling, persistence to disk, and
 * external API calls.
 */
public final class Main {

    // CardLayout identifiers used for top-level screen routing.
    private static final String SCREEN_START = "start";
    private static final String SCREEN_CREATE = "create";
    private static final String SCREEN_LOAD = "load";
    private static final String SCREEN_DASHBOARD = "dashboard";
    private static final String SCREEN_FLASHCARDS = "flashcards";
    private static final String SCREEN_PRACTICE = "practice";
    private static final String SCREEN_RESOURCES = "resources";
    private static final String SCREEN_QUIZ = "quiz";

    // API configuration.
    private static final String GROQ_API_KEY_PRIMARY = System.getenv("GROQ_API_KEY_PRIMARY");
    private static final String GROQ_API_KEY_SECONDARY = System.getenv("GROQ_API_KEY_SECONDARY");
    private static final String GROQ_MODEL_OPENAI = "openai/gpt-oss-120b";
    private static final String GROQ_MODEL_FALLBACK = "openai/gpt-oss-20b";
    private static final int GROQ_ATTEMPTS_PER_COMBINATION = 3;
    private static final int GROQ_MAX_OUTPUT_TOKENS = 4096;
    private static final int GROQ_MIN_OUTPUT_TOKENS = 256;
    private static final int GROQ_TPM_BUDGET = 8000;
    private static final int GROQ_TOKEN_SAFETY_MARGIN = 500;
    private static final int GROQ_QUESTION_BATCH_SIZE = 6;
    private static final int GROQ_SOURCE_CHUNK_CHARS = 6000;
    private static final int GROQ_FORBIDDEN_ITEMS_PER_BATCH = 30;

    // Core color palette.
    private static final Color COLOR_WHITE = Color.WHITE;
    private static final Color COLOR_NAVY = new Color(20, 45, 83);
    private static final Color COLOR_AMBER = new Color(214, 147, 34);
    private static final Color COLOR_LIGHT_GRAY = new Color(232, 236, 241);
    private static final Color COLOR_MID_GRAY = new Color(122, 132, 150);
    private static final Color COLOR_TEXT_DARK = new Color(38, 44, 56);
    private static final Color COLOR_SUCCESS = new Color(41, 165, 98);

    // Additional theme tokens for cards, buttons, and navigation.
    private static final Color COLOR_BG = new Color(245, 247, 250);
    private static final Color COLOR_CARD = Color.WHITE;
    private static final Color COLOR_BORDER = new Color(210, 214, 220);
    private static final Color COLOR_DISABLED_BG = new Color(185, 190, 198);
    private static final Color COLOR_DISABLED_TEXT = new Color(85, 90, 100);
    private static final Color COLOR_SIDEBAR_BG = new Color(21, 39, 79);
    private static final Color COLOR_SIDEBAR_BTN = new Color(34, 57, 108);
    private static final Color COLOR_BTN_PRIMARY = COLOR_NAVY;
    private static final Color COLOR_BTN_SECONDARY = COLOR_AMBER;
    private static final Color COLOR_BTN_SUCCESS = new Color(22, 128, 62);
    private static final Color COLOR_BTN_TEAL = new Color(18, 120, 130);
    private static final Color COLOR_BTN_GHOST = new Color(226, 231, 240);
    private static final Color COLOR_TILE_BLUE = new Color(33, 74, 135);
    private static final Color COLOR_TILE_AMBER = new Color(214, 147, 34);
    private static final Color COLOR_TILE_TEAL = new Color(26, 140, 120);
    private static final Color COLOR_TILE_PURPLE = new Color(120, 74, 160);

    private static final int MAX_FLASHCARDS = 30;
    private static final int DEMO_MIN_TEXT_SIZE = 27;
    private static final int DEMO_POPUP_MIN_WIDTH = 980;
    private static final int DEMO_POPUP_MIN_HEIGHT = 560;
    private static final int MAX_PRACTICE = 30;
    private static final int DEFAULT_OPEN_RESPONSE_PERCENT = 30;
    private static final int DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT = 25;
    private static final double GROQ_TEMPERATURE_STUDY_SET = 0.00;
    private static final double GROQ_TEMPERATURE_QUESTION_GENERATION = 0.40;
    private static final double GROQ_TEMPERATURE_ANSWER_CHECK = 0.30;

    private static final String[] LOGO_RESOURCE_CANDIDATES = {
        "/ConceptLabLogo.png",
        "/conceptlablogo.png",
        "/assets/ConceptLabLogo.png",
        "/images/ConceptLabLogo.png"
    };

    private static final String[] LOGO_FILE_CANDIDATES = {
        "ConceptLabLogo.png",
        "ConceptLabLogo.PNG",
        "conceptlablogo.png"
    };

    // Input size limits that keep copy-paste behavior stable.
    private static final int LIMIT_TITLE_CHARS = 80;
    private static final int LIMIT_SOURCE_CHARS = 12000;
    private static final int LIMIT_GOALS_CHARS = 1500;
    private static final int LIMIT_INSTRUCTIONS_CHARS = 1500;
    private static final int LIMIT_QUIZ_ANSWER_CHARS = 3000;

    private static final Font FONT_BASE = new Font("Segoe UI", Font.PLAIN, 27);
    private static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 30);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 36);
    private static final Font FONT_FIELD_LABEL = new Font("Segoe UI", Font.BOLD, 27);
    private static final Font FONT_HELPER = new Font("Segoe UI", Font.ITALIC, 27);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 27);
    private static final int MAX_CONTENT_WIDTH = 1080;

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0");

    // Local application storage locations.
    private final Path appHome = Paths.get(System.getProperty("user.home"), ".conceptlab");
    private final Path setsDir = appHome.resolve("sets");

    // Cached study sets loaded from disk and reverse lookup by set id.
    private final List<StudySet> availableSets = new ArrayList<>();
    private final Map<String, Path> setPathsById = new HashMap<>();
    private final Set<String> usedGeneratedPromptKeys = new HashSet<>();

    // Root frame and screen container.
    private JFrame frame;
    private JPanel rootCards;
    private java.awt.CardLayout cardLayout;

    // Header and global navigation widgets.
    private JLabel headerTitleLabel;
    private JButton backButton;
    private JButton menuButton;
    private Runnable backAction = () -> {
    };

    // Context bar and sidebar state.
    private JPopupMenu appMenu;
    private final Map<String, JButton> sidebarButtons = new HashMap<>();
    private String activeScreenId = SCREEN_START;
    private JPanel contextBarPanel;
    private JLabel contextSetNameLabel;
    private JLabel contextStatsLineLabel;

    // Active domain state for loaded set and quiz session.
    private StudySet currentSet;
    private GenerationInputs lastGenerationInputs;
    private QuizSession activeSession;
    private String quizReturnScreen = SCREEN_DASHBOARD;

    // Create screen inputs.
    private JTextField createTitleField;
    private JTextArea createSourceArea;
    private JTextArea createTopicGoalsArea;
    private JTextArea createInstructionsArea;
    private JSpinner createFlashcardsSpinner;
    private JSlider createDifficultySlider;
    private JCheckBox createChallengeCheck;

    // Dashboard widgets.
    private JLabel dashboardTitleLabel;
    private JLabel dashboardStatsLabel;
    private JLabel dashboardBestLabel;
    private ProgressRing dashboardRing;

    // Flashcard view widgets.
    private JLabel flashcardIndexLabel;
    private JLabel flashcardTopicLabel;
    private JLabel flashcardSideLabel;
    private JLabel flashcardFlipHintLabel;
    private JTextArea flashcardCardArea;
    private JPanel flashcardCardPanel;
    private boolean flashcardShowingBack;
    private int currentFlashcardIndex;

    // List screens.
    private JPanel resourcesListPanel;
    private JPanel loadSetListPanel;
    private JTextField loadSearchField;

    // Quiz widgets.
    private JLabel quizMetaLabel;
    private JTextArea quizPromptArea;
    private final JRadioButton[] quizChoiceButtons = new JRadioButton[4];
    private final ButtonGroup quizChoicesGroup = new ButtonGroup();
    private java.awt.CardLayout quizAnswerCardLayout;
    private JPanel quizAnswerCardPanel;
    private JTextArea quizFreeResponseArea;
    private JButton quizSubmitButton;
    private JButton quizNextButton;
    private JLabel quizFeedbackTitle;
    private JTextArea quizFeedbackBody;
    private JPanel quizFeedbackResourcesPanel;
    private JPanel quizFeedbackFlashcardsPanel;
    private JProgressBar quizProgressBar;
    private JButton quizBackToQuestionButton;

    /**
     * Application entry point.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            configureDemoUiDefaults();
            new Main().start();
        });
    }

    /**
     * Expands Swing UI defaults for large-font demo readability.
     */
    private static void configureDemoUiDefaults() {
        for (Enumeration<?> keys = UIManager.getDefaults().keys(); keys.hasMoreElements();) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font font && font.getSize() < DEMO_MIN_TEXT_SIZE) {
                UIManager.put(key, new FontUIResource(font.deriveFont((float) DEMO_MIN_TEXT_SIZE)));
            }
        }
        UIManager.put("OptionPane.minimumSize", new Dimension(DEMO_POPUP_MIN_WIDTH, DEMO_POPUP_MIN_HEIGHT));
        UIManager.put("ProgressBar.repaintInterval", 30);
        UIManager.put("ProgressBar.cycleTime", 900);
    }

    /**
     * Boots storage, constructs the main frame, and shows the start screen.
     */
    private void start() {
        ensureStorageDirectory();
        reloadSetsFromDisk(false);

        frame = new JFrame("ConceptLab");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(960, screen.width - 80);
        int maxH = Math.max(700, screen.height - 80);
        int w = Math.min(Math.max((int) Math.round(screen.width * 0.90), 1400), maxW);
        int h = Math.min(Math.max((int) Math.round(screen.height * 0.90), 900), maxH);
        frame.setSize(new Dimension(w, h));
        frame.setMinimumSize(new Dimension(Math.min(1280, maxW), Math.min(860, maxH)));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                attemptExit();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG);

        JPanel centerShell = new JPanel(new BorderLayout());
        centerShell.setBackground(COLOR_BG);
        centerShell.add(buildHeader(), BorderLayout.NORTH);

        cardLayout = new java.awt.CardLayout();
        rootCards = new JPanel(cardLayout);
        rootCards.setBackground(COLOR_BG);

        rootCards.add(wrapScreenForDemo(buildStartScreen()), SCREEN_START);
        rootCards.add(wrapScreenForDemo(buildCreateScreen()), SCREEN_CREATE);
        rootCards.add(wrapScreenForDemo(buildLoadScreen()), SCREEN_LOAD);
        rootCards.add(wrapScreenForDemo(buildDashboardScreen()), SCREEN_DASHBOARD);
        rootCards.add(wrapScreenForDemo(buildFlashcardsScreen()), SCREEN_FLASHCARDS);
        rootCards.add(wrapScreenForDemo(buildPracticeScreen()), SCREEN_PRACTICE);
        rootCards.add(wrapScreenForDemo(buildResourcesScreen()), SCREEN_RESOURCES);
        rootCards.add(wrapScreenForDemo(buildQuizScreen()), SCREEN_QUIZ);

        JPanel contentShell = new JPanel(new BorderLayout(0, 12));
        contentShell.setOpaque(false);
        contentShell.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contentShell.add(buildContextBar(), BorderLayout.NORTH);
        contentShell.add(rootCards, BorderLayout.CENTER);

        centerShell.add(contentShell, BorderLayout.CENTER);

        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(centerShell, BorderLayout.CENTER);
        frame.setContentPane(root);

        showStartScreen();
        ensureDemoReadability(frame.getContentPane());
        frame.setVisible(true);
    }

        /**
     * Wraps a screen panel in a scroll container for large-font demo layouts.
     */
    private JComponent wrapScreenForDemo(JComponent content) {
        JScrollPane scrollPane = new JScrollPane(
                content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(30);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(30);
        return scrollPane;
    }

    /**
     * Ensures all visible text respects the demo minimum font size.
     */
    private void ensureDemoReadability(Component root) {
        if (root == null) {
            return;
        }
        enforceMinimumFontTree(root);
        if (root instanceof JComponent jc) {
            jc.revalidate();
        }
        root.repaint();
    }

    /**
     * Applies minimum-size fonts recursively for a component subtree.
     */
    private static void enforceMinimumFontTree(Component component) {
        if (component == null) {
            return;
        }

        Font current = component.getFont();
        if (current != null && current.getSize() < DEMO_MIN_TEXT_SIZE) {
            component.setFont(current.deriveFont((float) DEMO_MIN_TEXT_SIZE));
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                enforceMinimumFontTree(child);
            }
        }
    }

    /**
     * Applies a cross-platform progress-bar UI so the moving runner renders on macOS.
     */
    private static void styleProgressBarForDemo(JProgressBar progressBar, Color color, boolean indeterminate) {
        if (progressBar == null) {
            return;
        }
        progressBar.setUI(new BasicProgressBarUI());
        progressBar.putClientProperty("JProgressBar.style", "bar");
        progressBar.setBackground(COLOR_LIGHT_GRAY);
        progressBar.setForeground(color);
        progressBar.setBorderPainted(false);
        progressBar.setIndeterminate(indeterminate);
    }

    /**
     * Packs and enlarges dialogs so oversized demo fonts stay readable.
     */
    private void normalizeDemoDialog(JDialog dialog) {
        if (dialog == null) {
            return;
        }
        ensureDemoReadability(dialog.getContentPane());
        dialog.pack();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = Math.max(640, screen.width - 80);
        int maxH = Math.max(420, screen.height - 80);
        int w = Math.min(Math.max(dialog.getWidth(), DEMO_POPUP_MIN_WIDTH), maxW);
        int h = Math.min(Math.max(dialog.getHeight(), DEMO_POPUP_MIN_HEIGHT), maxH);
        dialog.setSize(w, h);
        dialog.setMinimumSize(new Dimension(Math.min(DEMO_POPUP_MIN_WIDTH, maxW), Math.min(DEMO_POPUP_MIN_HEIGHT, maxH)));
        dialog.setLocationRelativeTo(frame);
    }

    /**
     * Document filter that enforces hard character limits during typing and paste.
     */
    private static final class LengthLimitFilter extends DocumentFilter {

        private final int maxChars;

        private LengthLimitFilter(int maxChars) {
            this.maxChars = Math.max(1, maxChars);
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) {
                return;
            }

            int current = fb.getDocument().getLength();
            int available = maxChars - current;
            if (available <= 0) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            String clipped = string.length() <= available ? string : string.substring(0, available);
            super.insertString(fb, offset, clipped, attr);

            if (clipped.length() < string.length()) {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }

            int current = fb.getDocument().getLength();
            int available = maxChars - (current - length);
            if (available <= 0) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            String clipped = text.length() <= available ? text : text.substring(0, available);
            super.replace(fb, offset, length, clipped, attrs);

            if (clipped.length() < text.length()) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    /**
     * Attaches a length-limiting filter to a text component when possible.
     */
    private static void applyCharLimit(JTextComponent comp, int maxChars) {
        if (comp == null) {
            return;
        }
        if (comp.getDocument() instanceof AbstractDocument) {
            ((AbstractDocument) comp.getDocument()).setDocumentFilter(new LengthLimitFilter(maxChars));
        }
    }

    /**
     * Applies consistent scroll-pane styling for editable multiline fields.
     */
    private JScrollPane wrapEditableArea(JTextArea area, int preferredHeightPx) {
        JScrollPane sp = new JScrollPane(
                area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        sp.setBorder(BorderFactory.createLineBorder(COLOR_LIGHT_GRAY));
        sp.getViewport().setBackground(COLOR_WHITE);
        sp.setPreferredSize(new Dimension(200, preferredHeightPx));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredHeightPx));
        return sp;
    }

    /**
     * Builds the top header row with menu and back controls.
     */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        header.setBackground(COLOR_CARD);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        menuButton = createThemedButton("Menu", COLOR_BTN_GHOST);
        menuButton.setForeground(COLOR_NAVY);
        sizeButtonForText(menuButton, 120, 48);
        menuButton.addActionListener(this::showAppMenu);

        backButton = createThemedButton("\u2190 Back", COLOR_BTN_GHOST);
        backButton.setForeground(COLOR_NAVY);
        sizeButtonForText(backButton, 156, 48);
        backButton.addActionListener(event -> {
            if (backAction != null) {
                backAction.run();
            }
        });

        left.add(menuButton);
        left.add(backButton);

        headerTitleLabel = new JLabel("ConceptLab", SwingConstants.CENTER);
        headerTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        headerTitleLabel.setForeground(COLOR_NAVY);

        int headerControlWidth = menuButton.getPreferredSize().width
                + backButton.getPreferredSize().width
                + 30;
        left.setPreferredSize(new Dimension(headerControlWidth, 52));

        JPanel rightSpacer = new JPanel();
        rightSpacer.setOpaque(false);
        rightSpacer.setPreferredSize(left.getPreferredSize());

        header.add(left, BorderLayout.WEST);
        header.add(headerTitleLabel, BorderLayout.CENTER);
        header.add(rightSpacer, BorderLayout.EAST);

        appMenu = buildAppMenu();
        return header;
    }

    /**
     * Sizes fixed controls from their rendered text so large demo fonts do not
     * collapse into Swing's ellipsis behavior.
     */
    private static void sizeButtonForText(JButton button, int minWidth, int minHeight) {
        Dimension preferred = button.getPreferredSize();
        Dimension size = new Dimension(
                Math.max(minWidth, preferred.width + 8),
                Math.max(minHeight, preferred.height)
        );
        button.setPreferredSize(size);
        button.setMinimumSize(size);
    }

    /**
     * Builds the compact context panel that summarizes the active study set.
     */
    private JPanel buildContextBar() {
        contextBarPanel = createCardContainer();
        contextBarPanel.setLayout(new BoxLayout(contextBarPanel, BoxLayout.Y_AXIS));
        contextBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        contextSetNameLabel = new JLabel("No study set loaded");
        contextSetNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        contextSetNameLabel.setForeground(COLOR_TEXT_DARK);

        contextStatsLineLabel = new JLabel("Flashcards 0 | Practice 0 | Unit test 0 | Resources 0");
        contextStatsLineLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        contextStatsLineLabel.setForeground(COLOR_MID_GRAY);

        contextBarPanel.add(contextSetNameLabel);
        contextBarPanel.add(Box.createVerticalStrut(2));
        contextBarPanel.add(contextStatsLineLabel);
        return contextBarPanel;
    }

    /**
     * Builds the persistent left sidebar used for primary navigation.
     */
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(COLOR_SIDEBAR_BG);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
        sidebar.setPreferredSize(new Dimension(230, 0));

        JLabel title = new JLabel("ConceptLab");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Study smarter.");
        subtitle.setForeground(new Color(196, 207, 230));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(18));

        sidebar.add(createSidebarButton("Dashboard", SCREEN_DASHBOARD, () -> {
            if (currentSet == null) {
                showStartScreen();
            } else {
                showDashboardScreen();
            }
        }));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarButton("Flashcards", SCREEN_FLASHCARDS, () -> {
            if (ensureSetLoaded()) {
                refreshFlashcardsPanel();
                showFlashcardsScreen();
            }
        }));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarButton("Practice", SCREEN_PRACTICE, () -> {
            if (ensureSetLoaded()) {
                showPracticeScreen();
            }
        }));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarButton("Unit Test", SCREEN_QUIZ, this::unitTestFlow));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarButton("Resources", SCREEN_RESOURCES, () -> {
            if (ensureSetLoaded()) {
                refreshResourcesPanel();
                showResourcesScreen();
            }
        }));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarButton("Create", SCREEN_CREATE, this::showCreateScreen));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarButton("Load", SCREEN_LOAD, this::showLoadScreen));
        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    /**
     * Creates one styled sidebar button and binds its action callback.
     */
    private JButton createSidebarButton(String label, String screenId, Runnable action) {
        JButton button = createThemedButton(label, COLOR_SIDEBAR_BTN);
        button.setForeground(Color.WHITE);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setPreferredSize(new Dimension(190, 40));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> action.run());
        sidebarButtons.put(screenId + "::" + label, button);
        return button;
    }

    /**
     * Updates sidebar button highlighting to match the active screen.
     */
    private void updateSidebarSelection(String screenId) {
        activeScreenId = screenId;
        for (Map.Entry<String, JButton> entry : sidebarButtons.entrySet()) {
            boolean active = entry.getKey().startsWith(screenId + "::");
            JButton button = entry.getValue();
            if (active) {
                button.setBackground(COLOR_BTN_SECONDARY);
                button.setForeground(COLOR_TEXT_DARK);
            } else {
                button.setBackground(COLOR_SIDEBAR_BTN);
                button.setForeground(Color.WHITE);
            }
        }
    }

    /**
     * Recomputes context summary text from the currently loaded set.
     */
    private void updateContextBar() {
        if (contextSetNameLabel == null || contextStatsLineLabel == null) {
            return;
        }
        if (currentSet == null) {
            contextSetNameLabel.setText("No study set loaded");
            contextStatsLineLabel.setText("Flashcards 0 | Practice 0 | Unit test 0 | Resources 0");
            return;
        }

        contextSetNameLabel.setText(currentSet.getTitle());
        contextStatsLineLabel.setText(
                "Flashcards " + currentSet.getFlashcards().size()
                + " | Practice " + currentSet.getPracticeQuestions().size()
                + " | Unit test " + currentSet.getUnitTestQuestions().size()
                + " | Resources " + currentSet.getResources().size());
    }

    /**
     * Builds the header overflow menu with create, load, save, and delete
     * actions.
     */
    private JPopupMenu buildAppMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem newSetItem = new JMenuItem("+ New Set");
        newSetItem.addActionListener(event -> {
            clearCreateForm();
            showCreateScreen();
        });

        JMenuItem listSetsItem = new JMenuItem("List Sets");
        listSetsItem.addActionListener(event -> showLoadScreen());

        JMenuItem deleteSetItem = new JMenuItem("Delete Set");
        deleteSetItem.addActionListener(event -> deleteSetFlow());

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(event -> saveCurrentSetWithToast());

        JMenuItem resetUsedBankItem = new JMenuItem("Reset Used Bank");
        resetUsedBankItem.addActionListener(event -> {
            usedGeneratedPromptKeys.clear();
            showInfo("Used generated-question bank was reset.");
        });

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(event -> attemptExit());

        menu.add(newSetItem);
        menu.add(listSetsItem);
        menu.add(deleteSetItem);
        menu.add(saveItem);
        menu.add(resetUsedBankItem);
        menu.addSeparator();
        menu.add(exitItem);
        return menu;
    }

    /**
     * Builds the landing screen shown before a set is selected.
     */
    private JPanel buildStartScreen() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(COLOR_BG);

        JPanel card = createCardContainer();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 760));
        card.setMaximumSize(new Dimension(620, Integer.MAX_VALUE));

        JLabel logoLabel = createLogoLabel(430);
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setMaximumSize(new Dimension(500, 500));

        JLabel subtitle = new JLabel("Create a study set or load an existing one.");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitle.setForeground(COLOR_TEXT_DARK);

        JButton createButton = createThemedButton("Create New Study Set", COLOR_BTN_SUCCESS);
        createButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        createButton.setPreferredSize(new Dimension(340, 48));
        createButton.setMaximumSize(new Dimension(340, 48));
        createButton.addActionListener(event -> {
            clearCreateForm();
            showCreateScreen();
        });

        JButton loadButton = createThemedButton("Load Existing Study Set", COLOR_BTN_TEAL);
        loadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadButton.setPreferredSize(new Dimension(340, 48));
        loadButton.setMaximumSize(new Dimension(340, 48));
        loadButton.addActionListener(event -> showLoadScreen());

        card.add(logoLabel);
        card.add(Box.createVerticalStrut(20));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(32));
        card.add(createButton);
        card.add(Box.createVerticalStrut(12));
        card.add(loadButton);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(24, 24, 24, 24);
        root.add(card, gbc);

        return root;
    }

    /**
     * Builds the study-set creation form screen.
     */
    private JPanel buildCreateScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG);
        // Main vertical form where children use LEFT_ALIGNMENT for consistent BoxLayout sizing.
        JPanel formShell = new JPanel();
        formShell.setOpaque(false);
        formShell.setLayout(new BoxLayout(formShell, BoxLayout.Y_AXIS));
        formShell.setBorder(BorderFactory.createEmptyBorder(14, 24, 24, 24));
        // Title section.
        JPanel titleSection = createSectionPanel("<html>Title <span style='color:#D32F2F'>*</span></html>");
        createTitleField = new JTextField();
        applyCharLimit(createTitleField, LIMIT_TITLE_CHARS);
        createTitleField.setFont(FONT_BASE);
        createTitleField.setAlignmentX(Component.LEFT_ALIGNMENT);
        createTitleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        titleSection.add(createTitleField);
        titleSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        formShell.add(titleSection);
        formShell.add(createVerticalGap(14));
        // Source material section.
        JPanel sourceSection = createSectionPanel("<html>Source Material <span style='color:#D32F2F'>*</span></html>");
        createSourceArea = createEditableArea(10);
        applyCharLimit(createSourceArea, LIMIT_SOURCE_CHARS);
        JScrollPane sourceScroll = wrapEditableArea(createSourceArea, 250);
        sourceScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        sourceSection.add(sourceScroll);
        sourceSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        sourceSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        formShell.add(sourceSection);
        formShell.add(createVerticalGap(14));
        // Goals and instructions section.
        JPanel goalsInstructionsSection = createSectionPanel("Goals + Instructions");
        JPanel twoCol = new JPanel(new GridLayout(1, 2, 16, 0));
        twoCol.setOpaque(false);
        twoCol.setAlignmentX(Component.LEFT_ALIGNMENT);
        twoCol.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        createTopicGoalsArea = createEditableArea(6);
        applyCharLimit(createTopicGoalsArea, LIMIT_GOALS_CHARS);
        JPanel goalsBlock = fieldBlock("Topic and goals", wrapEditableArea(createTopicGoalsArea, 165), true);
        twoCol.add(goalsBlock);

        createInstructionsArea = createEditableArea(6);
        applyCharLimit(createInstructionsArea, LIMIT_INSTRUCTIONS_CHARS);
        JPanel instructionsBlock = fieldBlock("Custom instructions", wrapEditableArea(createInstructionsArea, 165));
        twoCol.add(instructionsBlock);

        goalsInstructionsSection.add(twoCol);
        JLabel goalsNudge = new JLabel("Be specific: list exact concepts, problem types, and skills to master.");
        goalsNudge.setFont(FONT_HELPER);
        goalsNudge.setForeground(COLOR_MID_GRAY);
        goalsNudge.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalsInstructionsSection.add(goalsNudge);
        goalsInstructionsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalsInstructionsSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        formShell.add(goalsInstructionsSection);
        formShell.add(createVerticalGap(14));
        // Generation settings section.
        JPanel settingsSection = createSectionPanel("Generation Settings");
        JPanel numericRow = new JPanel(new GridLayout(1, 1, 16, 0));
        numericRow.setOpaque(false);
        numericRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        numericRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        createFlashcardsSpinner = new JSpinner(new SpinnerNumberModel(15, 1, MAX_FLASHCARDS, 1));
        createFlashcardsSpinner.setFont(new Font("Segoe UI", Font.BOLD, 16));
        createFlashcardsSpinner.setPreferredSize(new Dimension(160, 40));
        numericRow.add(fieldBlock("Flashcards (max " + MAX_FLASHCARDS + ")", createFlashcardsSpinner));
        settingsSection.add(numericRow);
        settingsSection.add(Box.createVerticalStrut(10));

        createDifficultySlider = new JSlider(0, 100, 60);
        createDifficultySlider.setOpaque(false);
        createDifficultySlider.setMajorTickSpacing(25);
        createDifficultySlider.setMinorTickSpacing(5);
        createDifficultySlider.setPaintTicks(true);
        createDifficultySlider.setPaintLabels(true);
        createDifficultySlider.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JPanel difficultyBlock = fieldBlock("Difficulty target (0.0 to 1.0)", createDifficultySlider);
        difficultyBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsSection.add(difficultyBlock);
        JLabel sliderHelper = new JLabel("0 = easier definitions, 1 = multi-step problems.");
        sliderHelper.setFont(FONT_HELPER);
        sliderHelper.setForeground(COLOR_MID_GRAY);
        sliderHelper.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsSection.add(sliderHelper);
        settingsSection.add(Box.createVerticalStrut(6));

        createChallengeCheck = new JCheckBox("Include challenge-style questions");
        createChallengeCheck.setOpaque(false);
        createChallengeCheck.setFont(new Font("Segoe UI", Font.BOLD, 15));
        createChallengeCheck.setSelected(true);
        createChallengeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsSection.add(createChallengeCheck);
        settingsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        formShell.add(settingsSection);
        formShell.add(createVerticalGap(20));
        // Centered generate action.
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        JButton generateButton = createThemedButton("Generate Study Set", COLOR_BTN_SECONDARY);
        generateButton.setPreferredSize(new Dimension(300, 48));
        generateButton.addActionListener(event -> generateSetFlow());
        buttonRow.add(generateButton);
        formShell.add(buttonRow);
        // Helper note and required-field legend.
        JLabel helperNote = new JLabel("Generation may take 15\u201360 seconds depending on content length.");
        helperNote.setFont(FONT_HELPER);
        helperNote.setForeground(COLOR_MID_GRAY);
        helperNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        helperNote.setHorizontalAlignment(SwingConstants.CENTER);
        helperNote.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        formShell.add(Box.createVerticalStrut(8));
        formShell.add(helperNote);

        JLabel requiredLegend = new JLabel("<html><span style='color:#D32F2F'>*</span> indicates a required field.</html>");
        requiredLegend.setFont(FONT_HELPER);
        requiredLegend.setForeground(COLOR_MID_GRAY);
        requiredLegend.setAlignmentX(Component.LEFT_ALIGNMENT);
        requiredLegend.setHorizontalAlignment(SwingConstants.CENTER);
        requiredLegend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        formShell.add(Box.createVerticalStrut(4));
        formShell.add(requiredLegend);
        // Scroll wrapper for the creation form.
        // Use BorderLayout.NORTH so form content can fill the viewport width.
        JPanel scrollContent = new JPanel(new BorderLayout());
        scrollContent.setOpaque(false);
        scrollContent.add(formShell, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getViewport().setBackground(COLOR_BG);
        root.add(scrollPane, BorderLayout.CENTER);

        return root;
    }

    /**
     * Alignment-safe vertical gap for BoxLayout containers.
     */
    private static JComponent createVerticalGap(int px) {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setAlignmentX(Component.LEFT_ALIGNMENT);
        gap.setPreferredSize(new Dimension(0, px));
        gap.setMinimumSize(new Dimension(0, px));
        gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, px));
        return gap;
    }

    /**
     * Creates a reusable card-like section container with a centered title.
     */
    private JPanel createSectionPanel(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(COLOR_CARD);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));

        // Center-aligned header label that stretches to full section width
        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 21));
        sectionLabel.setForeground(COLOR_TEXT_DARK);
        sectionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        section.add(sectionLabel);
        section.add(Box.createVerticalStrut(12));
        return section;
    }

    /**
     * Builds the load screen that lists existing saved sets.
     */
    private JPanel buildLoadScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel shell = new JPanel();
        shell.setOpaque(false);
        shell.setLayout(new BoxLayout(shell, BoxLayout.Y_AXIS));
        shell.setMaximumSize(new Dimension(MAX_CONTENT_WIDTH, Integer.MAX_VALUE));
        shell.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel heading = new JLabel("Load Study Set");
        heading.setFont(FONT_TITLE);
        heading.setForeground(COLOR_TEXT_DARK);
        shell.add(heading);
        shell.add(Box.createVerticalStrut(10));

        loadSearchField = new JTextField();
        loadSearchField.setFont(FONT_BASE);
        loadSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        loadSearchField.putClientProperty("JTextField.placeholderText", "Search study sets...");
        loadSearchField.addActionListener(e -> refreshLoadSetListPanel());
        loadSearchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                refreshLoadSetListPanel();
            }
        });
        shell.add(loadSearchField);
        shell.add(Box.createVerticalStrut(12));

        loadSetListPanel = new JPanel();
        loadSetListPanel.setBackground(COLOR_BG);
        loadSetListPanel.setLayout(new BoxLayout(loadSetListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(loadSetListPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setPreferredSize(new Dimension(0, 520));
        shell.add(scrollPane);
        shell.add(Box.createVerticalStrut(10));

        JButton refreshButton = createThemedButton("Refresh", COLOR_BTN_TEAL);
        sizeButtonForText(refreshButton, 150, 48);
        refreshButton.addActionListener(event -> {
            reloadSetsFromDisk(true);
            refreshLoadSetListPanel();
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.add(refreshButton);
        shell.add(south);

        content.add(shell);
        content.add(Box.createVerticalGlue());

        root.add(content, BorderLayout.CENTER);
        return root;
    }

    /**
     * Builds the dashboard that summarizes the loaded set and launch actions.
     */
    private JPanel buildDashboardScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel shell = new JPanel(new BorderLayout(0, 16));
        shell.setOpaque(false);
        shell.setMaximumSize(new Dimension(MAX_CONTENT_WIDTH, Integer.MAX_VALUE));

        JPanel headerRow = createCardContainer();
        headerRow.setLayout(new BorderLayout(16, 0));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        dashboardTitleLabel = new JLabel("No study set loaded");
        dashboardTitleLabel.setFont(FONT_TITLE);
        dashboardTitleLabel.setForeground(COLOR_TEXT_DARK);

        dashboardStatsLabel = new JLabel("Flashcards: 0 | Practice: 0 | Unit test: 0 | Resources: 0");
        dashboardStatsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dashboardStatsLabel.setForeground(COLOR_MID_GRAY);

        dashboardBestLabel = new JLabel("Best Test: N/A");
        dashboardBestLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        dashboardBestLabel.setForeground(COLOR_SUCCESS);

        titleBlock.add(dashboardTitleLabel);
        titleBlock.add(Box.createVerticalStrut(6));
        titleBlock.add(dashboardStatsLabel);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(dashboardBestLabel);
        headerRow.add(titleBlock, BorderLayout.CENTER);

        JPanel bestPanel = new JPanel(new BorderLayout(0, 4));
        bestPanel.setOpaque(false);
        dashboardRing = new ProgressRing(-1.0, 190);
        JLabel bestText = new JLabel("Best unit test");
        bestText.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bestText.setForeground(COLOR_MID_GRAY);
        bestText.setHorizontalAlignment(SwingConstants.CENTER);
        bestPanel.add(dashboardRing, BorderLayout.CENTER);
        bestPanel.add(bestText, BorderLayout.SOUTH);
        headerRow.add(bestPanel, BorderLayout.EAST);

        shell.add(headerRow, BorderLayout.NORTH);

        JPanel buttonGrid = new JPanel(new GridLayout(2, 2, 16, 16));
        buttonGrid.setOpaque(false);
        buttonGrid.add(createDashboardTile("Flashcards", "Review key cards", COLOR_TILE_BLUE, () -> {
            if (ensureSetLoaded()) {
                refreshFlashcardsPanel();
                showFlashcardsScreen();
            }
        }));
        buttonGrid.add(createDashboardTile("Practice", "Generate fresh quizzes", COLOR_TILE_AMBER, () -> {
            if (ensureSetLoaded()) {
                showPracticeScreen();
            }
        }));
        buttonGrid.add(createDashboardTile("Resources", "Links, sims, guides", COLOR_TILE_TEAL, () -> {
            if (ensureSetLoaded()) {
                refreshResourcesPanel();
                showResourcesScreen();
            }
        }));
        buttonGrid.add(createDashboardTile("Unit Test", "Comprehensive assessment", COLOR_TILE_PURPLE, this::unitTestFlow));
        shell.add(buttonGrid, BorderLayout.CENTER);

        root.add(shell, BorderLayout.CENTER);
        return root;
    }

    /**
     * Creates a clickable dashboard tile used as a large visual action target.
     */
    private JPanel createDashboardTile(String label, String subtitle, Color bgColor, Runnable action) {
        JPanel tile = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = Boolean.TRUE.equals(getClientProperty("hover"));
                Color draw = hover ? bgColor.brighter() : bgColor;
                g2.setColor(new Color(0, 0, 0, 28));
                g2.fillRoundRect(6, 8, getWidth() - 8, getHeight() - 8, 24, 24);
                g2.setColor(draw);
                g2.fillRoundRect(0, 0, getWidth() - 8, getHeight() - 8, 24, 24);
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        tile.setPreferredSize(new Dimension(200, 180));

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        nameLabel.setForeground(Color.WHITE);

        JLabel subLabel = new JLabel(subtitle);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subLabel.setForeground(new Color(255, 255, 255, 180));

        tile.add(nameLabel, BorderLayout.CENTER);
        tile.add(subLabel, BorderLayout.SOUTH);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                tile.putClientProperty("hover", Boolean.TRUE);
                ((JComponent) tile).repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                tile.putClientProperty("hover", Boolean.FALSE);
                ((JComponent) tile).repaint();
            }
        });
        return tile;
    }

    /**
     * Builds the flashcard review screen with flip and navigation controls.
     */
    private JPanel buildFlashcardsScreen() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(COLOR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        flashcardIndexLabel = new JLabel("Card 0 / 0");
        flashcardIndexLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        flashcardIndexLabel.setForeground(COLOR_NAVY);

        flashcardFlipHintLabel = new JLabel("Click the card to reveal the answer.");
        flashcardFlipHintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        flashcardFlipHintLabel.setForeground(COLOR_MID_GRAY);

        flashcardTopicLabel = new JLabel("Topic: -");
        flashcardTopicLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        flashcardTopicLabel.setForeground(COLOR_TEXT_DARK);

        top.add(flashcardIndexLabel);
        top.add(Box.createVerticalStrut(2));
        top.add(flashcardFlipHintLabel);
        top.add(Box.createVerticalStrut(6));
        top.add(flashcardTopicLabel);
        root.add(top, BorderLayout.NORTH);

        flashcardCardPanel = createCardContainer();
        flashcardCardPanel.setLayout(new BorderLayout(0, 12));
        flashcardCardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        flashcardCardPanel.setToolTipText("Click to flip this flashcard");

        flashcardSideLabel = new JLabel("Front");
        flashcardSideLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        flashcardSideLabel.setForeground(COLOR_NAVY);
        flashcardCardPanel.add(flashcardSideLabel, BorderLayout.NORTH);

        flashcardCardArea = createReadOnlyArea();
        flashcardCardArea.setFont(new Font("Segoe UI", Font.PLAIN, 27));
        flashcardCardArea.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        flashcardCardArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        flashcardCardArea.setToolTipText("Click to flip this flashcard");

        JScrollPane cardScroll = new JScrollPane(
                flashcardCardArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        cardScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        cardScroll.getViewport().setBackground(COLOR_CARD);
        cardScroll.getVerticalScrollBar().setUnitIncrement(20);
        cardScroll.setPreferredSize(new Dimension(0, 450));

        flashcardCardPanel.add(cardScroll, BorderLayout.CENTER);

        MouseAdapter flipper = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentSet == null || currentSet.getFlashcards().isEmpty()) {
                    return;
                }
                flashcardShowingBack = !flashcardShowingBack;
                renderCurrentFlashcard();
            }
        };
        flashcardCardPanel.addMouseListener(flipper);
        flashcardCardArea.addMouseListener(flipper);
        cardScroll.getViewport().addMouseListener(flipper);
        root.add(flashcardCardPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.setOpaque(false);

        JButton prevButton = createThemedButton("\u2190 Previous", COLOR_BTN_TEAL);
        prevButton.addActionListener(event -> {
            if (currentSet == null || currentSet.getFlashcards().isEmpty()) {
                return;
            }
            currentFlashcardIndex = Math.max(0, currentFlashcardIndex - 1);
            flashcardShowingBack = false;
            renderCurrentFlashcard();
        });

        JButton nextButton = createThemedButton("Next \u2192", COLOR_BTN_PRIMARY);
        nextButton.addActionListener(event -> {
            if (currentSet == null || currentSet.getFlashcards().isEmpty()) {
                return;
            }
            currentFlashcardIndex = Math.min(currentSet.getFlashcards().size() - 1, currentFlashcardIndex + 1);
            flashcardShowingBack = false;
            renderCurrentFlashcard();
        });

        actions.add(prevButton);
        actions.add(nextButton);
        root.add(actions, BorderLayout.SOUTH);

        return root;
    }

    /**
     * Builds the practice launcher screen for generating custom quizzes.
     */
    private JPanel buildPracticeScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel shell = new JPanel();
        shell.setOpaque(false);
        shell.setLayout(new BoxLayout(shell, BoxLayout.Y_AXIS));
        shell.setMaximumSize(new Dimension(MAX_CONTENT_WIDTH, Integer.MAX_VALUE));

        JLabel title = new JLabel("Practice");
        title.setFont(FONT_TITLE);
        title.setForeground(COLOR_TEXT_DARK);

        JLabel sub = new JLabel("Generate a fresh quiz from your study set content.");
        sub.setFont(FONT_BASE);
        sub.setForeground(COLOR_MID_GRAY);

        JButton generateNewQuizButton = createThemedButton("Generate New Quiz", COLOR_BTN_SECONDARY);
        generateNewQuizButton.setForeground(COLOR_TEXT_DARK);
        generateNewQuizButton.setPreferredSize(new Dimension(280, 48));
        generateNewQuizButton.addActionListener(event -> generateNewQuizFlow());

        JPanel actionCard = createCardContainer();
        actionCard.setLayout(new BoxLayout(actionCard, BoxLayout.Y_AXIS));
        actionCard.add(title);
        actionCard.add(Box.createVerticalStrut(8));
        actionCard.add(sub);
        actionCard.add(Box.createVerticalStrut(20));
        actionCard.add(generateNewQuizButton);

        shell.add(actionCard);
        shell.add(Box.createVerticalGlue());
        root.add(shell, BorderLayout.CENTER);

        return root;
    }

    /**
     * Builds the resource list screen.
     */
    private JPanel buildResourcesScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));

        JLabel title = new JLabel("Resources");
        title.setFont(FONT_TITLE);
        title.setForeground(COLOR_TEXT_DARK);
        root.add(title, BorderLayout.NORTH);

        resourcesListPanel = new JPanel();
        resourcesListPanel.setBackground(COLOR_BG);
        resourcesListPanel.setLayout(new BoxLayout(resourcesListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(resourcesListPanel);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 0, 0, 0),
                BorderFactory.createLineBorder(COLOR_BORDER)));
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        root.add(scrollPane, BorderLayout.CENTER);

        return root;
    }

    /**
     * Builds the quiz-taking screen used by both practice and unit tests.
     */
    private JPanel buildQuizScreen() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(COLOR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));
        // Top row with quiz title and progress bar.
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setOpaque(false);

        quizMetaLabel = new JLabel("Quiz");
        quizMetaLabel.setFont(FONT_TITLE);
        quizMetaLabel.setForeground(COLOR_TEXT_DARK);
        topPanel.add(quizMetaLabel, BorderLayout.NORTH);

        quizProgressBar = new JProgressBar(0, 100);
        quizProgressBar.setValue(0);
        quizProgressBar.setStringPainted(true);
        quizProgressBar.setPreferredSize(new Dimension(0, DEMO_MIN_TEXT_SIZE + 18));
        styleProgressBarForDemo(quizProgressBar, COLOR_SUCCESS, false);
        topPanel.add(quizProgressBar, BorderLayout.SOUTH);

        root.add(topPanel, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        quizPromptArea = createReadOnlyArea();
        quizPromptArea.setRows(4);
        center.add(labeledPanel("Question", quizPromptArea));
        center.add(Box.createVerticalStrut(10));

        JPanel choicesPanel = new JPanel(new GridLayout(4, 1, 0, 8));
        choicesPanel.setOpaque(false);
        for (int i = 0; i < quizChoiceButtons.length; i++) {
            quizChoiceButtons[i] = new JRadioButton("Choice " + (i + 1));
            quizChoiceButtons[i].setOpaque(false);
            quizChoiceButtons[i].setFont(FONT_BASE);
            quizChoicesGroup.add(quizChoiceButtons[i]);
            choicesPanel.add(quizChoiceButtons[i]);
        }

        quizFreeResponseArea = createEditableArea(6);
        applyCharLimit(quizFreeResponseArea, LIMIT_QUIZ_ANSWER_CHARS);
        quizFreeResponseArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JScrollPane freeResponseScroll = new JScrollPane(
                quizFreeResponseArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        freeResponseScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        freeResponseScroll.setPreferredSize(new Dimension(0, 150));
        freeResponseScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        freeResponseScroll.getViewport().setBackground(COLOR_WHITE);

        quizAnswerCardLayout = new java.awt.CardLayout();
        quizAnswerCardPanel = new JPanel(quizAnswerCardLayout);
        quizAnswerCardPanel.setOpaque(false);
        quizAnswerCardPanel.add(choicesPanel, "MCQ");
        quizAnswerCardPanel.add(freeResponseScroll, "SHORT");
        center.add(labeledPanel("Answer", quizAnswerCardPanel));

        center.add(Box.createVerticalStrut(10));

        quizFeedbackTitle = new JLabel(" ");
        quizFeedbackTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        quizFeedbackTitle.setForeground(COLOR_NAVY);

        quizFeedbackBody = createReadOnlyArea();
        quizFeedbackBody.setRows(6);
        JScrollPane feedbackBodyScroll = new JScrollPane(quizFeedbackBody);
        feedbackBodyScroll.setPreferredSize(new Dimension(0, 160));
        feedbackBodyScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        quizFeedbackResourcesPanel = new JPanel();
        quizFeedbackResourcesPanel.setOpaque(false);
        quizFeedbackResourcesPanel.setLayout(new BoxLayout(quizFeedbackResourcesPanel, BoxLayout.Y_AXIS));
        JScrollPane resourcesScroll = new JScrollPane(quizFeedbackResourcesPanel);
        resourcesScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        resourcesScroll.setPreferredSize(new Dimension(0, 190));
        resourcesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        resourcesScroll.getViewport().setBackground(COLOR_BG);
        resourcesScroll.getVerticalScrollBar().setUnitIncrement(18);

        quizFeedbackFlashcardsPanel = new JPanel();
        quizFeedbackFlashcardsPanel.setOpaque(false);
        quizFeedbackFlashcardsPanel.setLayout(new BoxLayout(quizFeedbackFlashcardsPanel, BoxLayout.Y_AXIS));
        JScrollPane flashcardsScroll = new JScrollPane(quizFeedbackFlashcardsPanel);
        flashcardsScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        flashcardsScroll.setPreferredSize(new Dimension(0, 180));
        flashcardsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        flashcardsScroll.getViewport().setBackground(COLOR_BG);
        flashcardsScroll.getVerticalScrollBar().setUnitIncrement(18);

        center.add(quizFeedbackTitle);
        center.add(Box.createVerticalStrut(4));
        center.add(feedbackBodyScroll);
        center.add(Box.createVerticalStrut(8));
        center.add(resourcesScroll);
        center.add(Box.createVerticalStrut(6));
        center.add(flashcardsScroll);

        JScrollPane scrollPane = new JScrollPane(center);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(COLOR_BG);
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        controls.setOpaque(false);

        quizSubmitButton = createThemedButton("Submit Answer", COLOR_BTN_SUCCESS);
        quizSubmitButton.addActionListener(event -> submitCurrentQuizAnswer());

        quizNextButton = createThemedButton("Next Question", COLOR_BTN_PRIMARY);
        quizNextButton.addActionListener(event -> advanceQuiz());

        quizBackToQuestionButton = createThemedButton("Back to Question", COLOR_BTN_TEAL);
        quizBackToQuestionButton.setVisible(false);
        quizBackToQuestionButton.addActionListener(event -> {
            quizBackToQuestionButton.setVisible(false);
            quizPromptArea.requestFocusInWindow();
        });

        controls.add(quizSubmitButton);
        controls.add(quizNextButton);
        controls.add(quizBackToQuestionButton);
        root.add(controls, BorderLayout.SOUTH);

        return root;
    }

    /**
     * Creates the standard rounded white container used across screens.
     */
    private JPanel createCardContainer() {
        JPanel card = new JPanel();
        card.setBackground(COLOR_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER),
                BorderFactory.createEmptyBorder(28, 32, 28, 32)
        ));
        return card;
    }

    /**
     * Navigates to the start screen.
     */
    private void showStartScreen() {
        setHeaderState("ConceptLab", false, true, () -> {
        });
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_START);
        cardLayout.show(rootCards, SCREEN_START);
        ensureDemoReadability(rootCards);
    }

    /**
     * Navigates to the create screen.
     */
    private void showCreateScreen() {
        setHeaderState("Create a Study Set", true, true, this::showStartScreen);
        setContextBarVisible(false);
        updateContextBar();
        updateSidebarSelection(SCREEN_CREATE);
        cardLayout.show(rootCards, SCREEN_CREATE);
        ensureDemoReadability(rootCards);
    }

    /**
     * Navigates to the load screen and refreshes on-disk set listing.
     */
    private void showLoadScreen() {
        Runnable goBack = currentSet == null ? this::showStartScreen : this::showDashboardScreen;
        setHeaderState("Load", true, true, goBack);
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_LOAD);
        cardLayout.show(rootCards, SCREEN_LOAD);
        ensureDemoReadability(rootCards);
        refreshLoadSetListPanel();
    }

    /**
     * Navigates to the dashboard and refreshes summary state.
     */
    private void showDashboardScreen() {
        refreshDashboard();
        setHeaderState("Study", true, true, this::showStartScreen);
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_DASHBOARD);
        cardLayout.show(rootCards, SCREEN_DASHBOARD);
        ensureDemoReadability(rootCards);
    }

    /**
     * Navigates to the flashcard screen.
     */
    private void showFlashcardsScreen() {
        setHeaderState("Study", true, true, this::showDashboardScreen);
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_FLASHCARDS);
        cardLayout.show(rootCards, SCREEN_FLASHCARDS);
        ensureDemoReadability(rootCards);
    }

    /**
     * Navigates to the practice launcher screen.
     */
    private void showPracticeScreen() {
        setHeaderState("Practice", true, true, this::showDashboardScreen);
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_PRACTICE);
        cardLayout.show(rootCards, SCREEN_PRACTICE);
        ensureDemoReadability(rootCards);
    }

    /**
     * Navigates to the resources screen.
     */
    private void showResourcesScreen() {
        setHeaderState("Resources", true, true, this::showDashboardScreen);
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_RESOURCES);
        cardLayout.show(rootCards, SCREEN_RESOURCES);
        ensureDemoReadability(rootCards);
    }

    /**
     * Navigates to the quiz screen.
     */
    private void showQuizScreen() {
        setHeaderState(
                activeSession == null ? "Quiz" : activeSession.title,
                true,
                true,
                this::attemptLeaveActiveQuiz
        );
        setContextBarVisible(true);
        updateContextBar();
        updateSidebarSelection(SCREEN_QUIZ);
        cardLayout.show(rootCards, SCREEN_QUIZ);
        ensureDemoReadability(rootCards);
    }

    /**
     * Shows or hides the context bar panel.
     */
    private void setContextBarVisible(boolean visible) {
        if (contextBarPanel != null) {
            contextBarPanel.setVisible(visible);
        }
    }

    /**
     * Updates shared header title and control visibility for a screen
     * transition.
     */
    private void setHeaderState(String title, boolean showBack, boolean showMenu, Runnable newBackAction) {
        headerTitleLabel.setText(title);
        backButton.setVisible(showBack);
        menuButton.setVisible(showMenu);
        backAction = newBackAction;
    }

    /**
     * Opens the application menu anchored to the menu button.
     */
    private void showAppMenu(ActionEvent event) {
        appMenu.show(menuButton, 0, menuButton.getHeight());
    }

    /**
     * Validates create-form input and triggers async study-set generation.
     */
    private void generateSetFlow() {
        String title = createTitleField.getText() == null ? "" : createTitleField.getText().trim();
        String source = createSourceArea.getText() == null ? "" : createSourceArea.getText().trim();
        String goals = createTopicGoalsArea.getText() == null ? "" : createTopicGoalsArea.getText().trim();
        String instructions = createInstructionsArea.getText() == null ? "" : createInstructionsArea.getText().trim();
        int flashcards = ((Number) createFlashcardsSpinner.getValue()).intValue();
        int practiceCount = 0;
        flashcards = Math.min(flashcards, MAX_FLASHCARDS);
        double difficulty = clamp01(createDifficultySlider.getValue() / 100.0);
        boolean includeChallenge = createChallengeCheck.isSelected();

        if (title.isBlank()) {
            showError("Title is required.");
            return;
        }
        if (source.isBlank()) {
            showError("Source material is required.");
            return;
        }
        if (goals.isBlank()) {
            showError("Topic and goals are required.");
            return;
        }

        GenerationInputs inputs = new GenerationInputs(
                title,
                source,
                goals,
                instructions,
                flashcards,
                practiceCount,
                difficulty,
                includeChallenge
        );

        runWithLoading(
                "Generating Study Set",
                "Building flashcards, unit test bank, and resources...",
                () -> generateStudySet(inputs),
                set -> {
                    currentSet = set;
                    lastGenerationInputs = inputs;
                    usedGeneratedPromptKeys.clear();
                    upsertInAvailableSets(set, filePathForSet(set));
                    refreshDashboard();
                    showDashboardScreen();
                    showInfo("Study set generated successfully.");
                }
        );
    }

    /**
     * Builds a study set using API-first strategy with deterministic fallback
     * generators.
     */
    private StudySet generateStudySet(GenerationInputs inputs) {
        throwIfInterrupted();
        try {
            StudySet apiResult = generateStudySetViaApi(inputs);
            if (apiResult != null) {
                return apiResult;
            }
        } catch (Exception ex) {
            System.err.println("[ConceptLab] API generation failed, using fallback: " + ex.getMessage());
        }

        List<String> facets = extractFacets(inputs.sourceMaterial, inputs.topicGoals, inputs.customInstructions);
        String topic = deriveTopic(inputs.topicGoals, inputs.title);

        List<Flashcard> flashcards = generateFlashcards(facets, inputs.flashcardCount, topic);

        Set<String> blockedPromptKeys = new LinkedHashSet<>();
        Set<String> blockedCorrectKeys = new LinkedHashSet<>();

        List<Question> practice = List.of();

        int unitTarget = determineUnitTestLength(facets, inputs.practiceCount);
        List<Question> unitTest = generateQuestions(
                facets,
                unitTarget,
                0.75,
                true,
                DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT,
                topic,
                blockedPromptKeys,
                blockedCorrectKeys,
                true
        );

        List<ResourceLink> resources = generateResources(topic, inputs.sourceMaterial);
        return new StudySet(inputs.title, flashcards, practice, unitTest, resources, -1.0);
    }

    /**
     * Rebuilds the unit test bank for the current set and starts the test
     * session.
     */
    private void unitTestFlow() {
        if (!ensureSetLoaded()) {
            return;
        }
        // Always generate a fresh unit test for current content state.
        runWithLoading(
                "Generating Unit Test",
                "Creating a comprehensive, higher-difficulty unit test...",
                this::generateUnitTestFromCurrentSet,
                questions -> {
                    currentSet.replaceUnitTestQuestions(questions);
                    refreshDashboard();
                    saveCurrentSetQuietly();
                    startQuizSession(new QuizSession(currentSet.getUnitTestQuestions(), true, "Unit Test"), SCREEN_DASHBOARD);
                }
        );
    }

    /**
     * Generates a fresh unit-test question list while avoiding overlap with
     * practice items.
     */
    private List<Question> generateUnitTestFromCurrentSet() {
        List<String> facets = collectFacetsFromCurrentSet();
        if (facets.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate unit test because the set has no source facets.");
        }

        String topic = inferTopicFromSet(currentSet);
        Set<String> blockedPrompts = new LinkedHashSet<>();
        Set<String> blockedCorrect = new LinkedHashSet<>();

        for (Question question : currentSet.getPracticeQuestions()) {
            blockedPrompts.add(question.normalizedPromptKey());
            String ck = question.normalizedCorrectAnswerKey();
            if (!ck.isBlank()) {
                blockedCorrect.add(ck);
            }
        }

        int targetLength = determineUnitTestLength(facets, currentSet.getPracticeQuestions().size());

        try {
            List<Question> apiResult = generateQuestionsViaApi(
                    topic, facets, targetLength, 0.75, true,
                    DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT,
                    new LinkedHashSet<>(blockedPrompts), new LinkedHashSet<>(blockedCorrect), true);
            if (apiResult != null && apiResult.size() >= 18) {
                return apiResult;
            }
            if (apiResult != null && !apiResult.isEmpty()) {
                Set<String> supplementBlockedP = new LinkedHashSet<>(blockedPrompts);
                Set<String> supplementBlockedC = new LinkedHashSet<>(blockedCorrect);
                for (Question q : apiResult) {
                    supplementBlockedP.add(q.normalizedPromptKey());
                    supplementBlockedC.add(q.normalizedCorrectAnswerKey());
                }
                List<Question> supplement = generateQuestions(
                        facets,
                        Math.max(0, 18 - apiResult.size()),
                        0.75,
                        true,
                        DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT,
                        topic,
                        supplementBlockedP,
                        supplementBlockedC,
                        true
                );
                List<Question> merged = new ArrayList<>(apiResult);
                merged.addAll(supplement);
                return merged;
            }
        } catch (Exception ex) {
            System.err.println("[ConceptLab] API unit test generation failed, using fallback: " + ex.getMessage());
        }

        return generateQuestions(
                facets,
                targetLength,
                0.75,
                true,
                DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT,
                topic,
                blockedPrompts,
                blockedCorrect,
                true
        );
    }

    /**
     * Opens quiz settings dialog and starts a generated practice quiz.
     */
    private void generateNewQuizFlow() {
        if (!ensureSetLoaded()) {
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setBackground(COLOR_WHITE);

        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 20, 1));
        JSlider difficultySlider = new JSlider(0, 100, 60);
        difficultySlider.setBackground(COLOR_WHITE);
        JSlider openResponseSlider = new JSlider(0, 100, DEFAULT_OPEN_RESPONSE_PERCENT);
        openResponseSlider.setBackground(COLOR_WHITE);
        openResponseSlider.setMajorTickSpacing(25);
        openResponseSlider.setMinorTickSpacing(5);
        openResponseSlider.setPaintTicks(true);
        openResponseSlider.setPaintLabels(true);
        JCheckBox challengeToggle = new JCheckBox("Include challenge questions", true);
        challengeToggle.setBackground(COLOR_WHITE);
        JCheckBox avoidSeenToggle = new JCheckBox("Avoid questions you've already seen", true);
        avoidSeenToggle.setBackground(COLOR_WHITE);
        JCheckBox strictUniqueToggle = new JCheckBox("Strict uniqueness (no duplicate answers)", true);
        strictUniqueToggle.setBackground(COLOR_WHITE);

        panel.add(fieldBlock("Number of questions", countSpinner));
        panel.add(fieldBlock("Difficulty target (0.0 to 1.0)", difficultySlider));
        panel.add(fieldBlock("Question style mix (0 = mostly MCQ, 100 = mostly open response)", openResponseSlider));
        panel.add(challengeToggle);
        panel.add(avoidSeenToggle);
        panel.add(strictUniqueToggle);

        int result = JOptionPane.showConfirmDialog(
                frame,
                panel,
                "Generate New Quiz",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        int requestedCount = ((Number) countSpinner.getValue()).intValue();
        double difficulty = clamp01(difficultySlider.getValue() / 100.0);
        int openResponsePercent = Math.max(0, Math.min(100, openResponseSlider.getValue()));
        boolean includeChallenge = challengeToggle.isSelected();
        boolean avoidSeen = avoidSeenToggle.isSelected();
        boolean strictUniqueness = strictUniqueToggle.isSelected();

        runWithLoading(
                "Generating Quiz",
                "Generating fresh questions with uniqueness safeguards...",
                () -> generateFreshQuiz(requestedCount, difficulty, openResponsePercent, includeChallenge, avoidSeen, strictUniqueness),
                quiz -> {
                    if (quiz.isEmpty()) {
                        showInfo("Could not produce unique questions with current constraints.");
                        return;
                    }
                    if (quiz.size() < requestedCount) {
                        showInfo("Generated " + quiz.size() + " questions due to uniqueness constraints.");
                    }
                    startQuizSession(new QuizSession(quiz, false, "Generated Quiz"), SCREEN_PRACTICE);
                }
        );
    }

    /**
     * Generates a new practice quiz from current-set content and
     * caller-provided settings.
     */
    private List<Question> generateFreshQuiz(
            int requestedCount,
            double difficulty,
            int openResponsePercent,
            boolean includeChallenge,
            boolean avoidSeen,
            boolean strictUniqueness
    ) {
        List<String> facets = collectFacetsFromCurrentSet();
        if (facets.isEmpty()) {
            return List.of();
        }

        String topic = inferTopicFromSet(currentSet);

        Set<String> blockedPromptKeys = new LinkedHashSet<>();
        Set<String> blockedCorrectKeys = new LinkedHashSet<>();

        if (avoidSeen) {
            for (Question question : currentSet.getPracticeQuestions()) {
                blockedPromptKeys.add(question.normalizedPromptKey());
                if (strictUniqueness) {
                    String ck = question.normalizedCorrectAnswerKey();
                    if (!ck.isBlank()) {
                        blockedCorrectKeys.add(ck);
                    }
                }
            }
            for (Question question : currentSet.getUnitTestQuestions()) {
                blockedPromptKeys.add(question.normalizedPromptKey());
                if (strictUniqueness) {
                    String ck = question.normalizedCorrectAnswerKey();
                    if (!ck.isBlank()) {
                        blockedCorrectKeys.add(ck);
                    }
                }
            }
            blockedPromptKeys.addAll(usedGeneratedPromptKeys);
        }

        List<Question> quiz = null;
        try {
            quiz = generateQuestionsViaApi(
                    topic, facets, requestedCount, difficulty, includeChallenge,
                    openResponsePercent,
                    new LinkedHashSet<>(blockedPromptKeys), new LinkedHashSet<>(blockedCorrectKeys), false);
        } catch (Exception ex) {
            System.err.println("[ConceptLab] API quiz generation failed, using fallback: " + ex.getMessage());
        }
        if (quiz == null || quiz.isEmpty()) {
            quiz = generateQuestions(
                    facets,
                    requestedCount,
                    difficulty,
                    includeChallenge,
                    openResponsePercent,
                    topic,
                    blockedPromptKeys,
                    blockedCorrectKeys,
                    false
            );
        }

        for (Question question : quiz) {
            usedGeneratedPromptKeys.add(question.normalizedPromptKey());
        }

        return quiz;
    }

    /**
     * Initializes quiz state and moves the UI into active quiz mode.
     */
    private void startQuizSession(QuizSession session, String returnScreen) {
        Objects.requireNonNull(session, "session must not be null");
        if (session.questions.isEmpty()) {
            showInfo("No questions available.");
            return;
        }

        this.activeSession = session;
        this.quizReturnScreen = returnScreen;

        renderActiveQuizQuestion();
        showQuizScreen();
    }

    /**
     * Renders the current question and resets answer/feedback widgets for this
     * step.
     */
    private void renderActiveQuizQuestion() {
        if (activeSession == null) {
            return;
        }

        Question question = activeSession.currentQuestion();
        int number = activeSession.index + 1;

        quizMetaLabel.setText(activeSession.title + "  |  Question " + number + " of " + activeSession.questions.size());
        quizPromptArea.setText(question.getPrompt());
        quizPromptArea.setCaretPosition(0);
        quizChoicesGroup.clearSelection();
        quizFreeResponseArea.setText("");

        if (question.isMultipleChoice()) {
            String[] choices = question.getChoices();
            for (int i = 0; i < 4; i++) {
                String value = i < choices.length ? choices[i] : ("Option " + (char) ('A' + i));
                quizChoiceButtons[i].setText((char) ('A' + i) + ". " + value);
                quizChoiceButtons[i].setEnabled(true);
            }
            quizFreeResponseArea.setEditable(false);
            quizAnswerCardLayout.show(quizAnswerCardPanel, "MCQ");
        } else {
            for (int i = 0; i < 4; i++) {
                quizChoiceButtons[i].setText((char) ('A' + i) + ".");
                quizChoiceButtons[i].setEnabled(false);
            }
            quizFreeResponseArea.setEditable(true);
            quizAnswerCardLayout.show(quizAnswerCardPanel, "SHORT");
        }

        quizSubmitButton.setEnabled(true);
        quizNextButton.setEnabled(false);
        quizNextButton.setText(number == activeSession.questions.size() ? "Finish" : "Next Question");

        quizFeedbackTitle.setText(" ");
        quizFeedbackBody.setText("");
        quizFeedbackResourcesPanel.removeAll();
        quizFeedbackResourcesPanel.revalidate();
        quizFeedbackResourcesPanel.repaint();
        ensureDemoReadability(quizFeedbackResourcesPanel);
        quizFeedbackFlashcardsPanel.removeAll();
        quizFeedbackFlashcardsPanel.revalidate();
        quizFeedbackFlashcardsPanel.repaint();
        ensureDemoReadability(quizFeedbackFlashcardsPanel);
        quizBackToQuestionButton.setVisible(false);
        // Update progress bar for completed-question count.
        int progressPct = (int) Math.round((double) (number - 1) / activeSession.questions.size() * 100);
        quizProgressBar.setValue(progressPct);
        quizProgressBar.setString("Question " + number + " / " + activeSession.questions.size());
        ensureDemoReadability(quizAnswerCardPanel);
        ensureDemoReadability(quizAnswerCardPanel);
    }

    /**
     * Submits the current answer for evaluation and renders feedback details.
     */
    private void submitCurrentQuizAnswer() {
        if (activeSession == null) {
            return;
        }

        if (activeSession.answeredThisQuestion) {
            return;
        }

        Question question = activeSession.currentQuestion();
        Integer selected = null;
        String userAnswer;
        if (question.isMultipleChoice()) {
            int picked = selectedChoiceIndex();
            if (picked < 0) {
                showInfo("Select an answer before submitting.");
                return;
            }
            selected = picked;
            String[] choices = question.getChoices();
            userAnswer = picked < choices.length ? choices[picked] : "";
        } else {
            userAnswer = quizFreeResponseArea.getText() == null ? "" : quizFreeResponseArea.getText().trim();
            if (userAnswer.isBlank()) {
                showInfo("Enter your answer before submitting.");
                return;
            }
        }

        final Integer selectedIndex = selected;
        final String finalUserAnswer = userAnswer;
        runWithLoading(
                "Checking Answer",
                "Checking answer with AI and building detailed feedback...",
                () -> evaluateAnswerViaApi(question, finalUserAnswer, selectedIndex),
                result -> {
                    if (activeSession == null) {
                        return;
                    }

                    boolean correct = result.correct;
                    activeSession.markAnswer(selectedIndex != null ? selectedIndex : 0, correct);

                    for (JRadioButton button : quizChoiceButtons) {
                        button.setEnabled(false);
                    }
                    quizFreeResponseArea.setEditable(false);

                    quizSubmitButton.setEnabled(false);
                    quizNextButton.setEnabled(true);

                    quizFeedbackTitle.setText(correct ? "Correct" : "Not Correct");
                    quizFeedbackTitle.setForeground(correct ? COLOR_SUCCESS : COLOR_NAVY);
                    quizFeedbackBody.setText(result.feedback);
                    quizFeedbackBody.setCaretPosition(0);

                    renderFeedbackResources(question);
                    renderFeedbackFlashcards(question);
                }
        );
    }

    /**
     * Renders resource links related to the just-answered question.
     */
    private void renderFeedbackResources(Question question) {
        quizFeedbackResourcesPanel.removeAll();

        List<ResourceLink> related = findResourcesForQuestion(question);
        if (related.isEmpty()) {
            JLabel none = new JLabel("No related resources for this question.");
            none.setForeground(COLOR_MID_GRAY);
            none.setFont(FONT_BASE);
            quizFeedbackResourcesPanel.add(none);
        } else {
            JLabel heading = new JLabel("Related resources:");
            heading.setForeground(COLOR_NAVY);
            heading.setFont(new Font("Segoe UI", Font.BOLD, 13));
            quizFeedbackResourcesPanel.add(heading);
            quizFeedbackResourcesPanel.add(Box.createVerticalStrut(4));

            for (ResourceLink link : related) {
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

                JLabel label = new JLabel(link.getTitle() + " (" + link.getType().name() + ")");
                label.setFont(FONT_BASE);
                label.setForeground(COLOR_TEXT_DARK);

                JButton openButton = createThemedButton("Open", COLOR_BTN_TEAL);
                openButton.setPreferredSize(new Dimension(90, 30));
                openButton.addActionListener(event -> {
                    openInBrowser(link.getUrl());
                    quizBackToQuestionButton.setVisible(true);
                });

                row.add(label, BorderLayout.CENTER);
                row.add(openButton, BorderLayout.EAST);
                quizFeedbackResourcesPanel.add(row);
            }
        }

        quizFeedbackResourcesPanel.revalidate();
        quizFeedbackResourcesPanel.repaint();
        ensureDemoReadability(quizFeedbackResourcesPanel);
    }

    /**
     * Renders flashcards that overlap with the active question topic and
     * wording.
     */
    private void renderFeedbackFlashcards(Question question) {
        quizFeedbackFlashcardsPanel.removeAll();

        if (currentSet == null || currentSet.getFlashcards().isEmpty()) {
            quizFeedbackFlashcardsPanel.revalidate();
        quizFeedbackFlashcardsPanel.repaint();
        ensureDemoReadability(quizFeedbackFlashcardsPanel);
            return;
        }
        // Find flashcards with meaningful token overlap to the current question.
        String questionText = normalizeKey(question.getPrompt() + " " + question.getAnswerKey());
        String[] questionTokens = questionText.split("\\s+");

        List<int[]> scored = new ArrayList<>(); // [index, score]
        for (int i = 0; i < currentSet.getFlashcards().size(); i++) {
            Flashcard card = currentSet.getFlashcards().get(i);
            String cardText = normalizeKey(card.getFront() + " " + card.getBack());
            int overlap = 0;
            for (String token : questionTokens) {
                if (token.length() >= 4 && cardText.contains(token)) {
                    overlap++;
                }
            }
            if (overlap >= 2) {
                scored.add(new int[]{i, overlap});
            }
        }

        scored.sort((a, b) -> Integer.compare(b[1], a[1]));
        int limit = Math.min(scored.size(), 5);

        if (limit == 0) {
            quizFeedbackFlashcardsPanel.revalidate();
        quizFeedbackFlashcardsPanel.repaint();
        ensureDemoReadability(quizFeedbackFlashcardsPanel);
            return;
        }

        JLabel heading = new JLabel("Related flashcards:");
        heading.setForeground(COLOR_NAVY);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 13));
        quizFeedbackFlashcardsPanel.add(heading);
        quizFeedbackFlashcardsPanel.add(Box.createVerticalStrut(4));

        for (int j = 0; j < limit; j++) {
            int cardIndex = scored.get(j)[0];
            Flashcard card = currentSet.getFlashcards().get(cardIndex);

            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

            JLabel cardLabel = new JLabel(compactText(card.getFront(), 80));
            cardLabel.setFont(FONT_BASE);
            cardLabel.setForeground(COLOR_TEXT_DARK);

            JButton jumpBtn = createThemedButton("Open Card", COLOR_BTN_TEAL);
            jumpBtn.setPreferredSize(new Dimension(130, 30));
            final int idx = cardIndex;
            jumpBtn.addActionListener(e -> {
                showRelatedFlashcardDialog(card, idx, currentSet.getFlashcards().size());
            });

            row.add(cardLabel, BorderLayout.CENTER);
            row.add(jumpBtn, BorderLayout.EAST);
            quizFeedbackFlashcardsPanel.add(row);
        }

        quizFeedbackFlashcardsPanel.revalidate();
        quizFeedbackFlashcardsPanel.repaint();
        ensureDemoReadability(quizFeedbackFlashcardsPanel);
    }

    /**
     * Moves to the next question or finishes the quiz when the session is
     * complete.
     */
    private void advanceQuiz() {
        if (activeSession == null || !activeSession.answeredThisQuestion) {
            return;
        }

        if (activeSession.isLastQuestion()) {
            finishQuizSession();
            return;
        }

        activeSession.moveNext();
        renderActiveQuizQuestion();
    }

    /**
     * Finalizes quiz scoring, persists progress, and returns to the appropriate
     * screen.
     */
    private void finishQuizSession() {
        if (activeSession == null) {
            return;
        }

        activeSession.finished = true;
        double percent = 100.0 * activeSession.correctAnswers / activeSession.questions.size();

        if (activeSession.unitTest && currentSet != null) {
            currentSet.updateBestUnitTestPercent(percent);
            refreshDashboard();
            saveCurrentSetQuietly();
        }

        String message = "Score: "
                + activeSession.correctAnswers
                + " / "
                + activeSession.questions.size()
                + " ("
                + PERCENT_FORMAT.format(percent)
                + "%)";
        showInfo(message);

        activeSession = null;
        navigateAfterQuiz();
    }

    /**
     * Restores the screen that launched the current quiz session.
     */
    private void navigateAfterQuiz() {
        if (SCREEN_PRACTICE.equals(quizReturnScreen)) {
            showPracticeScreen();
            return;
        }
        showDashboardScreen();
    }

    /**
     * Handles user attempts to leave the quiz and protects unsaved progress.
     */
    private void attemptLeaveActiveQuiz() {
        if (activeSession == null) {
            navigateAfterQuiz();
            return;
        }

        if (activeSession.hasUnsavedProgress()) {
            int choice = JOptionPane.showConfirmDialog(
                    frame,
                    "Your progress in this quiz/test will not be saved. Leave anyway?",
                    "Leave Quiz",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        activeSession = null;
        navigateAfterQuiz();
    }

    /**
     * Returns selected MCQ index or -1 when no option is selected.
     */
    private int selectedChoiceIndex() {
        for (int i = 0; i < quizChoiceButtons.length; i++) {
            if (quizChoiceButtons[i].isSelected()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Recomputes dashboard labels and progress ring from current-set state.
     */
    private void refreshDashboard() {
        if (currentSet == null) {
            dashboardTitleLabel.setText("No study set loaded");
            dashboardStatsLabel.setText("Flashcards: 0 | Practice: 0 | Unit test: 0 | Resources: 0");
            dashboardBestLabel.setText("Best Test: N/A");
            if (dashboardRing != null) {
                dashboardRing.setPercent(-1.0);
                dashboardRing.setToolTipText("Best unit test score");
            }
            updateContextBar();
            return;
        }

        dashboardTitleLabel.setText(currentSet.getTitle());
        dashboardStatsLabel.setText(
                "Flashcards: "
                + currentSet.getFlashcards().size()
                + " | Practice: "
                + currentSet.getPracticeQuestions().size()
                + " | Unit test: "
                + currentSet.getUnitTestQuestions().size()
                + " | Resources: "
                + currentSet.getResources().size()
        );

        double best = currentSet.getBestUnitTestPercent();
        if (best < 0.0) {
            dashboardBestLabel.setText("Best Test: N/A");
        } else {
            dashboardBestLabel.setText("Best Test: " + PERCENT_FORMAT.format(best) + "%");
        }

        if (dashboardRing != null) {
            dashboardRing.setPercent(best);
            dashboardRing.setToolTipText("Best unit test score");
        }
        updateContextBar();
    }

    /**
     * Resets flashcard index and renders the first card.
     */
    private void refreshFlashcardsPanel() {
        currentFlashcardIndex = 0;
        flashcardShowingBack = false;
        renderCurrentFlashcard();
    }

    /**
     * Draws the current flashcard side and updates index metadata.
     */
    private void renderCurrentFlashcard() {
        if (currentSet == null || currentSet.getFlashcards().isEmpty()) {
            flashcardIndexLabel.setText("Card 0 / 0");
            flashcardTopicLabel.setText("Topic: -");
            flashcardSideLabel.setText("Front");
            flashcardFlipHintLabel.setText("Click the card to reveal the answer.");
            flashcardCardArea.setText("No flashcards available.\n\nGenerate or load a set with flashcards.");
            return;
        }

        List<Flashcard> cards = currentSet.getFlashcards();
        currentFlashcardIndex = Math.max(0, Math.min(currentFlashcardIndex, cards.size() - 1));

        Flashcard card = cards.get(currentFlashcardIndex);
        flashcardIndexLabel.setText("Card " + (currentFlashcardIndex + 1) + " / " + cards.size());
        flashcardTopicLabel.setText("Topic: " + card.getTopic());
        if (flashcardShowingBack) {
            flashcardSideLabel.setText("Back");
            flashcardFlipHintLabel.setText("Click the card to return to the prompt.");
            flashcardCardArea.setText(card.getBack());
        } else {
            flashcardSideLabel.setText("Front");
            flashcardFlipHintLabel.setText("Click the card to reveal the answer.");
            flashcardCardArea.setText(card.getFront());
        }
        flashcardCardArea.setCaretPosition(0);
    }

    /**
     * Rebuilds the resource list panel using current-set links.
     */
    private void refreshResourcesPanel() {
        resourcesListPanel.removeAll();

        if (currentSet == null || currentSet.getResources().isEmpty()) {
            JLabel label = new JLabel("No resources available.");
            label.setForeground(COLOR_MID_GRAY);
            label.setFont(FONT_BASE);
            resourcesListPanel.add(label);
            resourcesListPanel.revalidate();
            resourcesListPanel.repaint();
            return;
        }

        for (ResourceLink resource : currentSet.getResources()) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(COLOR_WHITE);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_BORDER),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));

            JLabel label = new JLabel(resource.getTitle() + "  [" + resource.getType().name() + "]");
            label.setFont(FONT_BASE);
            label.setForeground(COLOR_TEXT_DARK);

            JButton openButton = createThemedButton("Open", COLOR_BTN_SECONDARY);
            openButton.setForeground(COLOR_TEXT_DARK);
            openButton.setPreferredSize(new Dimension(92, 34));
            openButton.addActionListener(event -> openInBrowser(resource.getUrl()));

            row.add(label, BorderLayout.CENTER);
            row.add(openButton, BorderLayout.EAST);
            resourcesListPanel.add(row);
            resourcesListPanel.add(Box.createVerticalStrut(10));
        }

        resourcesListPanel.revalidate();
        resourcesListPanel.repaint();
    }

    /**
     * Rebuilds the set list filtered by the load-screen search field.
     */
    private void refreshLoadSetListPanel() {
        loadSetListPanel.removeAll();

        String filter = loadSearchField == null ? "" : loadSearchField.getText().trim().toLowerCase(Locale.ROOT);
        List<StudySet> visibleSets = new ArrayList<>();
        for (StudySet set : availableSets) {
            if (filter.isBlank() || set.getTitle().toLowerCase(Locale.ROOT).contains(filter)
                    || set.getId().toLowerCase(Locale.ROOT).contains(filter)) {
                visibleSets.add(set);
            }
        }

        if (visibleSets.isEmpty()) {
            JLabel empty = new JLabel(filter.isBlank() ? "No saved study sets found." : "No study sets match your search.");
            empty.setForeground(COLOR_MID_GRAY);
            empty.setFont(FONT_BASE);
            loadSetListPanel.add(empty);
            loadSetListPanel.revalidate();
            loadSetListPanel.repaint();
            return;
        }

        visibleSets.sort(Comparator.comparing(StudySet::getTitle, String.CASE_INSENSITIVE_ORDER));

        for (StudySet set : visibleSets) {
            JPanel row = new JPanel(new GridBagLayout());
            row.setBackground(COLOR_WHITE);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_BORDER),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

            ProgressRing ring = new ProgressRing(set.getBestUnitTestPercent(), 56);
            ring.setToolTipText("Best unit test score");

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.gridx = 0;
            gbc.insets = new Insets(0, 0, 0, 12);
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.NONE;
            row.add(ring, gbc);

            JPanel center = new JPanel();
            center.setBackground(COLOR_WHITE);
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

            JLabel title = new JLabel(set.getTitle());
            title.setFont(new Font("Segoe UI", Font.BOLD, 18));
            title.setForeground(COLOR_TEXT_DARK);

            JLabel stats = new JLabel("Cards: " + set.getFlashcards().size()
                    + "  |  Practice: " + set.getPracticeQuestions().size()
                    + "  |  Unit test: " + set.getUnitTestQuestions().size());
            stats.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            stats.setForeground(COLOR_MID_GRAY);

            center.add(title);
            center.add(Box.createVerticalStrut(4));
            center.add(stats);

            gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.CENTER;
            row.add(center, gbc);

            JButton loadButton = createThemedButton("Load", COLOR_BTN_SECONDARY);
            loadButton.setForeground(COLOR_TEXT_DARK);
            loadButton.setPreferredSize(new Dimension(120, 38));
            loadButton.setMinimumSize(new Dimension(120, 38));
            loadButton.addActionListener(event -> {
                currentSet = set;
                usedGeneratedPromptKeys.clear();
                refreshDashboard();
                showDashboardScreen();
            });

            gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.gridx = 2;
            gbc.insets = new Insets(0, 12, 0, 0);
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.NONE;
            row.add(loadButton, gbc);

            loadSetListPanel.add(row);
            loadSetListPanel.add(Box.createVerticalStrut(10));
        }

        loadSetListPanel.revalidate();
        loadSetListPanel.repaint();
    }

    /**
     * Loads all persisted sets from disk into in-memory index structures.
     */
    private void reloadSetsFromDisk(boolean showErrors) {
        availableSets.clear();
        setPathsById.clear();

        ensureStorageDirectory();

        List<String> errors = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(setsDir, "*.clab")) {
            for (Path path : stream) {
                try {
                    StudySet set = StudySet.readFromFile(path);
                    if (setPathsById.containsKey(set.getId())) {
                        errors.add("Duplicate set id found while loading: " + set.getId() + " (" + path.getFileName() + ")");
                        continue;
                    }
                    availableSets.add(set);
                    setPathsById.put(set.getId(), path);
                } catch (Exception ex) {
                    errors.add(path.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            errors.add("Unable to read set directory: " + ex.getMessage());
        }

        if (showErrors && !errors.isEmpty()) {
            showError("Some files could not be loaded:\n\n" + String.join("\n", errors));
        }
    }

    /**
     * Saves the current set and surfaces a success or failure message.
     */
    private void saveCurrentSetWithToast() {
        if (!ensureSetLoaded()) {
            return;
        }
        try {
            Path path = saveCurrentSet();
            showInfo("Saved to: " + path);
        } catch (IOException ex) {
            showError("Save failed: " + ex.getMessage());
        }
    }

    /**
     * Saves the current set and logs errors without showing user dialogs.
     */
    private void saveCurrentSetQuietly() {
        if (currentSet == null) {
            return;
        }
        try {
            saveCurrentSet();
        } catch (IOException ignored) {
        }
    }

    /**
     * Persists the current set and updates in-memory file index metadata.
     */
    private Path saveCurrentSet() throws IOException {
        if (currentSet == null) {
            throw new IllegalStateException("No set loaded");
        }

        ensureStorageDirectory();
        Path path = filePathForSet(currentSet);
        currentSet.storeToFile(path);
        upsertInAvailableSets(currentSet, path);
        return path;
    }

    /**
     * Computes the canonical on-disk path for a given study set.
     */
    private Path filePathForSet(StudySet set) {
        Path existing = setPathsById.get(set.getId());
        if (existing != null) {
            return existing;
        }
        String fileName = set.getId() + "_" + set.getSafeFileStem() + ".clab";
        return setsDir.resolve(fileName);
    }

    /**
     * Inserts or replaces a set in the in-memory listing and path index.
     */
    private void upsertInAvailableSets(StudySet set, Path path) {
        setPathsById.put(set.getId(), path);
        for (int i = 0; i < availableSets.size(); i++) {
            if (availableSets.get(i).getId().equals(set.getId())) {
                availableSets.set(i, set);
                return;
            }
        }
        availableSets.add(set);
    }

    /**
     * Prompts for a set selection and deletes it from disk and in-memory
     * indexes.
     */
    private void deleteSetFlow() {
        if (availableSets.isEmpty()) {
            showInfo("No saved sets to delete.");
            return;
        }

        DefaultComboBoxModel<StudySet> model = new DefaultComboBoxModel<>();
        for (StudySet set : availableSets) {
            model.addElement(set);
        }

        JComboBox<StudySet> comboBox = new JComboBox<>(model);
        comboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getTitle() + " (" + value.getId() + ")");
            label.setOpaque(true);
            label.setBackground(isSelected ? COLOR_LIGHT_GRAY : COLOR_WHITE);
            label.setForeground(COLOR_TEXT_DARK);
            label.setFont(FONT_BASE);
            return label;
        });

        int choice = JOptionPane.showConfirmDialog(
                frame,
                comboBox,
                "Select set to delete",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        StudySet selected = (StudySet) comboBox.getSelectedItem();
        if (selected == null) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                frame,
                "Delete study set '" + selected.getTitle() + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        Path path = setPathsById.get(selected.getId());
        try {
            if (path != null && Files.exists(path)) {
                Files.delete(path);
            }
            availableSets.removeIf(set -> set.getId().equals(selected.getId()));
            setPathsById.remove(selected.getId());
            if (currentSet != null && currentSet.getId().equals(selected.getId())) {
                currentSet = null;
                showStartScreen();
            }
            refreshLoadSetListPanel();
            showInfo("Set deleted.");
        } catch (IOException ex) {
            showError("Delete failed: " + ex.getMessage());
        }
    }

    /**
     * Ensures local application storage directories exist.
     */
    private void ensureStorageDirectory() {
        try {
            Files.createDirectories(setsDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create app storage directory: " + setsDir, ex);
        }
    }

    /**
     * Handles window-close requests with optional progress warning.
     */
    private void attemptExit() {
        if (activeSession != null && activeSession.hasUnsavedProgress()) {
            int choice = JOptionPane.showConfirmDialog(
                    frame,
                    "Your progress in this quiz/test will not be saved. Leave anyway?",
                    "Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (currentSet != null) {
            int choice = JOptionPane.showConfirmDialog(
                    frame,
                    "Save current study set before exit?",
                    "Save",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    saveCurrentSet();
                } catch (IOException ex) {
                    showError("Could not save before exit: " + ex.getMessage());
                    return;
                }
            }
        }

        frame.dispose();
    }

    /**
     * Confirms a study set is loaded before entering set-dependent flows.
     */
    private boolean ensureSetLoaded() {
        if (currentSet == null) {
            showInfo("Create or load a study set first.");
            return false;
        }
        return true;
    }

    /**
     * Extracts normalized content facets from source, goals, and instructions
     * text.
     */
    private List<String> extractFacets(String source, String goals, String instructions) {
        String merged = String.join("\n", nullToEmpty(source), nullToEmpty(goals), nullToEmpty(instructions));
        String[] raw = merged.split("[\\n\\r\\t]+|(?<=[.!?])\\s+");

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String chunk : raw) {
            throwIfInterrupted();
            String cleaned = chunk == null ? "" : chunk.trim();
            if (cleaned.length() < 18) {
                continue;
            }
            String normalized = normalizeKey(cleaned);
            if (normalized.length() < 10) {
                continue;
            }
            unique.add(cleaned);
        }

        if (unique.isEmpty()) {
            String fallback = source == null ? "" : source.trim();
            if (!fallback.isBlank()) {
                unique.add(fallback);
            }
        }

        return new ArrayList<>(unique);
    }

    /**
     * Builds a facet pool from currently loaded set material.
     */
    private List<String> collectFacetsFromCurrentSet() {
        LinkedHashSet<String> facets = new LinkedHashSet<>();

        if (currentSet == null) {
            return List.of();
        }

        for (Flashcard card : currentSet.getFlashcards()) {
            facets.add(card.getFront());
            facets.add(card.getBack());
        }

        for (Question question : currentSet.getPracticeQuestions()) {
            facets.add(question.getPrompt());
            facets.add(question.getAnswerKey());
        }

        for (Question question : currentSet.getUnitTestQuestions()) {
            facets.add(question.getPrompt());
            facets.add(question.getAnswerKey());
        }

        List<String> filtered = new ArrayList<>();
        for (String facet : facets) {
            if (facet != null && normalizeKey(facet).length() >= 8) {
                filtered.add(facet.trim());
            }
        }
        return filtered;
    }

    /**
     * Generates deterministic fallback flashcards from extracted facets.
     */
    private List<Flashcard> generateFlashcards(List<String> facets, int count, String topic) {
        List<Flashcard> cards = new ArrayList<>();
        if (facets.isEmpty()) {
            return cards;
        }

        for (int i = 0; i < count; i++) {
            throwIfInterrupted();
            String facet = facets.get(i % facets.size());
            String front = "Explain this concept: " + compactText(facet, 120);
            String back = "Key idea: " + compactText(facet, 260);
            cards.add(new Flashcard(topic, front, back));
        }

        return cards;
    }

    /**
     * Normalizes flashcard list size and uniqueness to requested count.
     */
    private List<Flashcard> enforceFlashcardCount(
            List<Flashcard> inputCards,
            int requestedCount,
            String defaultTopic,
            List<String> facetsForTopUp
    ) {
        int target = Math.max(1, Math.min(MAX_FLASHCARDS, requestedCount));
        LinkedHashMap<String, Flashcard> unique = new LinkedHashMap<>();

        if (inputCards != null) {
            for (Flashcard card : inputCards) {
                if (card == null) {
                    continue;
                }
                String front = nullToEmpty(card.getFront()).trim();
                String back = nullToEmpty(card.getBack()).trim();
                if (front.isBlank() || back.isBlank()) {
                    continue;
                }
                String cardTopic = nullToEmpty(card.getTopic()).trim();
                if (cardTopic.isBlank()) {
                    cardTopic = defaultTopic;
                }
                String key = normalizeKey(front) + "||" + normalizeKey(back);
                if (key.isBlank() || unique.containsKey(key)) {
                    continue;
                }
                unique.put(key, new Flashcard(cardTopic, front, back));
                if (unique.size() >= target) {
                    break;
                }
            }
        }

        List<String> seeds = facetsForTopUp == null ? List.of() : facetsForTopUp;
        int seedCursor = 0;
        while (unique.size() < target) {
            String seed = seeds.isEmpty() ? defaultTopic : seeds.get(seedCursor % seeds.size());
            seedCursor++;
            String front = "Explain this concept " + (unique.size() + 1) + ": " + compactText(seed, 120);
            String back = "Key idea: " + compactText(seed, 260) + " Include one concrete example.";
            String key = normalizeKey(front) + "||" + normalizeKey(back);
            if (unique.containsKey(key)) {
                back = back + " (" + seedCursor + ")";
                key = normalizeKey(front) + "||" + normalizeKey(back);
            }
            unique.put(key, new Flashcard(defaultTopic, front, back));
        }

        List<Flashcard> normalized = new ArrayList<>(unique.values());
        if (normalized.size() > target) {
            normalized = new ArrayList<>(normalized.subList(0, target));
        }
        return normalized;
    }

    /**
     * Generates deterministic fallback questions with MCQ and short-answer mix.
     */
    private List<Question> generateQuestions(
            List<String> facets,
            int requestedCount,
            double targetDifficulty,
            boolean includeChallenge,
            int openResponsePercent,
            String topic,
            Set<String> blockedPromptKeys,
            Set<String> blockedCorrectKeys,
            boolean emphasizeCoverage
    ) {
        List<Question> output = new ArrayList<>();
        if (requestedCount <= 0 || facets.isEmpty()) {
            return output;
        }

        int maxAttempts = Math.max(requestedCount * 40, 200);
        int attempts = 0;
        int facetCursor = 0;

        while (output.size() < requestedCount && attempts < maxAttempts) {
            throwIfInterrupted();
            attempts++;

            String facet = facets.get(facetCursor % facets.size());
            facetCursor++;

            boolean makeOpen = ThreadLocalRandom.current().nextInt(100) < Math.max(0, Math.min(100, openResponsePercent));
            String prompt = buildPrompt(facet, output.size() + 1, emphasizeCoverage, makeOpen);
            String promptKey = normalizeKey(prompt);
            if (blockedPromptKeys.contains(promptKey)) {
                continue;
            }

            double difficulty = adjustDifficulty(targetDifficulty, emphasizeCoverage);
            boolean challenge = includeChallenge && (difficulty >= 0.72 || ThreadLocalRandom.current().nextDouble() < 0.35);
            Question question;

            if (makeOpen) {
                question = new Question(
                        null,
                        topic,
                        prompt,
                        Question.ResponseType.SHORT_ANSWER,
                        new String[0],
                        -1,
                        new String[0],
                        "",
                        "",
                        difficulty,
                        challenge
                );
            } else {
                String correct = buildCorrectChoice(facet);
                String correctKey = normalizeKey(correct);
                if (blockedCorrectKeys.contains(correctKey)) {
                    correct = correct + " (source-aligned statement)";
                    correctKey = normalizeKey(correct);
                    if (blockedCorrectKeys.contains(correctKey)) {
                        continue;
                    }
                }

                String[] choices = buildChoices(facets, facet, correct, topic);
                int correctIndex = -1;
                for (int i = 0; i < choices.length; i++) {
                    if (choices[i].equals(correct)) {
                        correctIndex = i;
                        break;
                    }
                }
                if (correctIndex < 0) {
                    continue;
                }
                String[] feedback = buildFeedbackArray(choices, correctIndex, facet);
                question = new Question(topic, prompt, choices, correctIndex, feedback, difficulty, challenge);
            }

            output.add(question);

            blockedPromptKeys.add(question.normalizedPromptKey());
            String correctKey = question.normalizedCorrectAnswerKey();
            if (!correctKey.isBlank()) {
                blockedCorrectKeys.add(correctKey);
            }
        }

        return output;
    }

    /**
     * Builds MCQ options with one correct answer and plausible distractors.
     */
    private String[] buildChoices(List<String> facets, String sourceFacet, String correct, String topic) {
        List<String> choices = new ArrayList<>();
        choices.add(correct);

        int cursor = 0;
        while (choices.size() < 4 && cursor < facets.size() * 5) {
            throwIfInterrupted();
            String candidate = misconceptionChoice(sourceFacet, topic, cursor++);
            if (isChoiceUnique(choices, candidate)) {
                choices.add(candidate);
            }
        }

        int fallback = 0;
        while (choices.size() < 4) {
            String candidate = fallbackDistractor(topic, fallback++);
            if (isChoiceUnique(choices, candidate)) {
                choices.add(candidate);
            }
        }

        Collections.shuffle(choices);
        return choices.toArray(new String[0]);
    }

    /**
     * Builds per-choice feedback text for generated MCQ items.
     */
    private String[] buildFeedbackArray(String[] choices, int correctIndex, String sourceFacet) {
        String[] feedback = new String[4];
        String clue = compactText(sourceFacet, 160);

        for (int i = 0; i < feedback.length; i++) {
            if (i == correctIndex) {
                feedback[i] = "Correct. Great job identifying the best-supported reasoning for this concept. "
                        + "The source points to this core idea: " + clue + ". "
                        + "To verify your logic, match the key terms in the prompt to the mechanism in the concept, "
                        + "then eliminate options that overgeneralize, reverse cause/effect, or ignore constraints.";
            } else {
                feedback[i] = "This option is not correct because it conflicts with the central mechanism in the concept. "
                        + "A common misconception is to treat related terms as interchangeable or ignore boundary conditions. "
                        + "Re-check the source idea: " + clue + ". "
                        + "A stronger approach is to identify what must be true for the concept, then test each option against that requirement.";
            }
        }

        return feedback;
    }

    /**
     * Generates a curated fallback resource list for a topic.
     */
    private List<ResourceLink> generateResources(String topic, String sourceMaterial) {
        List<ResourceLink> links = new ArrayList<>();
        String key = normalizeKey(topic + " " + sourceMaterial);

        if (key.contains("electric") || key.contains("field") || key.contains("potential") || key.contains("coulomb")) {
            links.add(new ResourceLink(topic, "PhET: Charges and Fields", ResourceType.SIMULATION,
                    "https://phet.colorado.edu/en/simulations/charges-and-fields"));
            links.add(new ResourceLink(topic, "PhET: Coulomb's Law", ResourceType.SIMULATION,
                    "https://phet.colorado.edu/en/simulations/coulombs-law"));
            links.add(new ResourceLink(topic, "Khan Academy: Electric charge, force, and voltage", ResourceType.ARTICLE,
                    "https://www.khanacademy.org/science/physics/electric-charge-electric-force-and-voltage"));
            links.add(new ResourceLink(topic, "Khan Academy: Electric potential energy", ResourceType.ARTICLE,
                    "https://www.khanacademy.org/science/physics/electric-charge-electric-force-and-voltage"));
            links.add(new ResourceLink(topic, "OpenStax: Electric Charge", ResourceType.REFERENCE,
                    "https://openstax.org/books/university-physics-volume-2/pages/5-1-electric-charge"));
            links.add(new ResourceLink(topic, "OpenStax: Electric Field", ResourceType.REFERENCE,
                    "https://openstax.org/books/university-physics-volume-2/pages/5-5-electric-field"));
            links.add(new ResourceLink(topic, "The Physics Classroom: Electric Fields", ResourceType.ARTICLE,
                    "https://www.physicsclassroom.com/class/estatics"));
        }

        if (key.contains("kinematic") || key.contains("velocity") || key.contains("acceleration")
                || key.contains("displacement") || key.contains("motion")) {
            links.add(new ResourceLink(topic, "PhET: Moving Man", ResourceType.SIMULATION,
                    "https://phet.colorado.edu/en/simulations/moving-man"));
            links.add(new ResourceLink(topic, "PhET: Forces and Motion Basics", ResourceType.SIMULATION,
                    "https://phet.colorado.edu/en/simulations/forces-and-motion-basics"));
            links.add(new ResourceLink(topic, "Khan Academy: One-dimensional motion", ResourceType.ARTICLE,
                    "https://www.khanacademy.org/science/physics/one-dimensional-motion"));
            links.add(new ResourceLink(topic, "Khan Academy: Two-dimensional motion", ResourceType.ARTICLE,
                    "https://www.khanacademy.org/science/physics/two-dimensional-motion"));
            links.add(new ResourceLink(topic, "OpenStax: Position, Displacement, and Average Velocity", ResourceType.REFERENCE,
                    "https://openstax.org/books/university-physics-volume-1/pages/3-1-position-displacement-and-average-velocity"));
            links.add(new ResourceLink(topic, "OpenStax: Motion with Constant Acceleration", ResourceType.REFERENCE,
                    "https://openstax.org/books/university-physics-volume-1/pages/3-4-motion-with-constant-acceleration"));
            links.add(new ResourceLink(topic, "The Physics Classroom: 1D Kinematics", ResourceType.ARTICLE,
                    "https://www.physicsclassroom.com/class/1DKin"));
        }

        if (links.size() < 10) {
            links.add(new ResourceLink(topic, "Khan Academy: Algebra fundamentals", ResourceType.ARTICLE,
                    "https://www.khanacademy.org/math/algebra"));
            links.add(new ResourceLink(topic, "OpenStax: Algebra and Trigonometry", ResourceType.REFERENCE,
                    "https://openstax.org/details/books/algebra-and-trigonometry-2e"));
            links.add(new ResourceLink(topic, "Wikipedia: " + topic, ResourceType.REFERENCE,
                    "https://en.wikipedia.org/wiki/" + encodeQuery(topic).replace("+", "_")));
            links.add(new ResourceLink(topic, "PhET Simulations Catalog", ResourceType.SIMULATION,
                    "https://phet.colorado.edu/en/simulations/browse"));
            links.add(new ResourceLink(topic, "OpenStax: College Physics", ResourceType.REFERENCE,
                    "https://openstax.org/details/books/college-physics-2e"));
            links.add(new ResourceLink(topic, "Khan Academy: Physics Library", ResourceType.ARTICLE,
                    "https://www.khanacademy.org/science/physics"));
            links.add(new ResourceLink(topic, "The Physics Classroom", ResourceType.ARTICLE,
                    "https://www.physicsclassroom.com/"));
            links.add(new ResourceLink(topic, "MIT OCW Intro Physics", ResourceType.VIDEO,
                    "https://ocw.mit.edu/courses/8-01sc-classical-mechanics-fall-2016/"));
        }

        return validateAndRepairResources(links, topic);
    }

    /**
     * Finds resources that best match a topic string.
     */
    private List<ResourceLink> findResourcesForTopic(String topic) {
        if (currentSet == null) {
            return List.of();
        }

        List<ResourceLink> exact = new ArrayList<>();
        List<ResourceLink> fallback = new ArrayList<>();

        String questionKey = normalizeKey(topic);
        for (ResourceLink link : currentSet.getResources()) {
            String linkKey = normalizeKey(link.getTopic());
            if (!questionKey.isBlank() && !linkKey.isBlank() && linkKey.contains(questionKey)) {
                exact.add(link);
            } else {
                fallback.add(link);
            }
        }

        if (!exact.isEmpty()) {
            return exact;
        }

        if (fallback.size() <= 12) {
            return fallback;
        }
        return fallback.subList(0, 12);
    }

    /**
     * Finds resources ranked by overlap with a specific question.
     */
    private List<ResourceLink> findResourcesForQuestion(Question question) {
        if (question == null || currentSet == null || currentSet.getResources().isEmpty()) {
            return List.of();
        }

        String key = normalizeKey(question.getTopic() + " " + question.getPrompt());
        String[] tokens = key.split("\\s+");

        List<ResourceLink> all = currentSet.getResources();
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            ResourceLink link = all.get(i);
            String text = normalizeKey(link.getTopic() + " " + link.getTitle() + " " + link.getUrl());
            int score = 0;
            for (String token : tokens) {
                if (token.length() >= 4 && text.contains(token)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new int[]{i, score});
            }
        }

        scored.sort((a, b) -> Integer.compare(b[1], a[1]));
        List<ResourceLink> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && out.size() < 12; i++) {
            out.add(all.get(scored.get(i)[0]));
        }
        if (out.isEmpty()) {
            out.addAll(findResourcesForTopic(question.getTopic()));
        }
        return out;
    }

    /**
     * Validates resource links and repairs unusable entries with safe fallback
     * URLs.
     */
    private List<ResourceLink> validateAndRepairResources(List<ResourceLink> links, String topic) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
        List<ResourceLink> validated = new ArrayList<>();
        int checks = 0;
        int failures = 0;
        int maxChecks = 25;

        for (ResourceLink link : links) {
            if (link == null || link.getUrl() == null || link.getUrl().isBlank()) {
                continue;
            }
            if (validated.size() >= 25) {
                break;
            }
            String url = link.getUrl().trim();
            if (!seenUrls.add(normalizeKey(url))) {
                continue;
            }
            if (!isLikelyDirectResourceUrl(url)) {
                failures++;
                continue;
            }

            if (checks >= maxChecks) {
                break;
            }
            checks++;
            boolean ok = isReachableUrl(client, url);
            if (ok) {
                validated.add(link);
            } else {
                failures++;
            }
        }

        if (validated.size() < links.size()) {
            System.err.println("[ConceptLab][Resources] kept " + validated.size() + "/" + links.size()
                    + " links after direct-link validation.");
        }
        return validated;
    }

    /**
     * Heuristically checks whether a URL likely points to direct content.
     */
    private boolean isLikelyDirectResourceUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                return false;
            }

            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (host.isBlank()) {
                return false;
            }
            if (host.contains("google.") || host.contains("bing.") || host.contains("duckduckgo.com")
                    || host.contains("search.yahoo.com")) {
                return false;
            }

            String path = Optional.ofNullable(uri.getPath()).orElse("").toLowerCase(Locale.ROOT);
            String query = Optional.ofNullable(uri.getQuery()).orElse("").toLowerCase(Locale.ROOT);
            String combined = host + path + "?" + query;

            if (path.isBlank() || "/".equals(path)) {
                return false;
            }
            if (path.contains("/search") || path.contains("/results") || path.contains("/find") || path.contains("/lookup")) {
                return false;
            }
            if (query.contains("page_search_query=") || query.contains("search_query=")
                    || query.startsWith("q=") || query.contains("&q=") || query.contains("query=")) {
                boolean youtubeWatch = host.contains("youtube.com") && path.startsWith("/watch");
                if (!youtubeWatch) {
                    return false;
                }
            }
            if (combined.contains("404") || combined.contains("not-found")
                    || combined.contains("not_found") || combined.contains("/error")) {
                return false;
            }
            if (host.contains("youtube.com")
                    && !(path.startsWith("/watch") || path.startsWith("/shorts/") || path.startsWith("/embed/"))) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Performs a lightweight network reachability check for a candidate URL.
     */
    private boolean isReachableUrl(HttpClient client, String url) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(head, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code >= 200 && code <= 399) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            HttpRequest get = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(get, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            return code >= 200 && code <= 399;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Builds a safe search URL when a direct resource URL cannot be trusted.
     */
    private String fallbackResourceSearchUrl(String topic, ResourceType type) {
        String q = encodeQuery((topic == null ? "" : topic) + " concept explanation");
        if (type == ResourceType.SIMULATION || type == ResourceType.INTERACTIVE) {
            return "https://phet.colorado.edu/en/search?q=" + q;
        }
        if (type == ResourceType.VIDEO) {
            return "https://www.youtube.com/results?search_query=" + q;
        }
        if (type == ResourceType.PRACTICE) {
            return "https://www.khanacademy.org/search?page_search_query=" + q;
        }
        return "https://www.google.com/search?q=" + q;
    }

    /**
     * Builds a readable prompt stem from one extracted facet.
     */
    private String buildPrompt(String facet, int questionNumber, boolean emphasizeCoverage, boolean shortAnswer) {
        String stem = compactText(facet, 100);
        if (shortAnswer) {
            if (emphasizeCoverage) {
                return "Coverage " + questionNumber + ": Explain and justify this concept in context: " + stem + ".";
            }
            return "Practice " + questionNumber + ": Provide a concise, reasoned answer about " + stem + ".";
        }
        if (emphasizeCoverage) {
            return "Coverage " + questionNumber + ": Which option best explains this concept in context: " + stem + "?";
        }
        return "Practice " + questionNumber + ": Which statement is most accurate about " + stem + "?";
    }

    /**
     * Builds the canonical correct MCQ answer text from a facet.
     */
    private String buildCorrectChoice(String facet) {
        return "This concept is best described as: " + compactText(facet, 140);
    }

    /**
     * Returns a generic distractor when facet-driven distractors are
     * insufficient.
     */
    private String fallbackDistractor(String topic, int index) {
        String normalizedTopic = topic == null || topic.isBlank() ? "the topic" : topic;
        return switch (index % 6) {
            case 0 ->
                "A statement that swaps cause and effect in " + normalizedTopic;
            case 1 ->
                "An explanation that ignores an essential condition in " + normalizedTopic;
            case 2 ->
                "A definition that incorrectly treats two related terms as identical";
            case 3 ->
                "An overgeneralization that sounds right but is not supported by evidence";
            case 4 ->
                "A detail that directly contradicts the core concept";
            default ->
                "A partially true claim that omits the main mechanism";
        };
    }

    /**
     * Builds a plausible misconception distractor tied to facet language.
     */
    private String misconceptionChoice(String facet, String topic, int index) {
        String concept = compactText(facet, 120);
        return switch (index % 5) {
            case 0 ->
                "This means the relationship always holds, even when required conditions are absent.";
            case 1 ->
                "This is equivalent to a nearby term, so there is no practical distinction to track.";
            case 2 ->
                "The concept is mostly about memorizing definitions, not reasoning from constraints.";
            case 3 ->
                "The correct approach is to infer the opposite direction of influence from " + compactText(topic, 40) + ".";
            default ->
                "This interpretation focuses on a detail from \"" + concept + "\" but misses the main principle.";
        };
    }

    /**
     * Slightly adjusts target difficulty based on generation context.
     */
    private double adjustDifficulty(double target, boolean emphasizeCoverage) {
        double variance = emphasizeCoverage ? 0.12 : 0.10;
        double value = target + ThreadLocalRandom.current().nextDouble(-variance, variance);
        if (emphasizeCoverage) {
            value = Math.max(value, 0.65);
        }
        return clamp01(value);
    }

    /**
     * Computes a unit-test length based on content breadth and prior practice
     * count.
     */
    private int determineUnitTestLength(List<String> facets, int practiceCount) {
        int coverageBase = Math.max(facets.size(), 10);
        int scaled = Math.max(practiceCount / 2 + 6, coverageBase);
        return Math.max(18, Math.min(Math.max(scaled, coverageBase), 60));
    }

    /**
     * Derives a display topic from goals text with title fallback.
     */
    private String deriveTopic(String topicGoals, String title) {
        String candidate = topicGoals == null ? "" : topicGoals.trim();
        if (!candidate.isBlank()) {
            String[] lines = candidate.split("[\\r\\n]+");
            if (lines.length > 0 && !lines[0].isBlank()) {
                return lines[0].trim();
            }
            return candidate;
        }
        return title;
    }

    /**
     * Infers topic from loaded set content when explicit topic data is absent.
     */
    private String inferTopicFromSet(StudySet set) {
        if (set == null) {
            return "General";
        }

        Optional<String> fromCards = set.getFlashcards().stream()
                .map(Flashcard::getTopic)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
        if (fromCards.isPresent()) {
            return fromCards.get();
        }

        Optional<String> fromQuestions = set.getPracticeQuestions().stream()
                .map(Question::getTopic)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
        if (fromQuestions.isPresent()) {
            return fromQuestions.get();
        }

        return set.getTitle();
    }

    /**
     * Runs background work behind a modal loading dialog with cancel support.
     */
    private <T> void runWithLoading(
            String title,
            String message,
            CheckedSupplier<T> task,
            java.util.function.Consumer<T> onSuccess
    ) {
        JDialog dialog = new JDialog(frame, title, true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        panel.setBackground(COLOR_WHITE);

        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(FONT_FIELD_LABEL);
        messageLabel.setForeground(COLOR_NAVY);

        JLabel factLabel = new JLabel(toHtml("Fact: " + LoadingScreenFacts.nextFact(), 68));
        factLabel.setFont(FONT_BASE);
        factLabel.setForeground(COLOR_TEXT_DARK);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(0, DEMO_MIN_TEXT_SIZE + 16));
        styleProgressBarForDemo(progressBar, COLOR_SUCCESS, true);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(COLOR_WHITE);
        center.add(messageLabel);
        center.add(Box.createVerticalStrut(10));
        center.add(factLabel);
        center.add(Box.createVerticalStrut(14));
        center.add(progressBar);

        JScrollPane centerScroll = new JScrollPane(
                center,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        centerScroll.setBorder(BorderFactory.createEmptyBorder());
        centerScroll.getViewport().setBackground(COLOR_WHITE);
        centerScroll.getVerticalScrollBar().setUnitIncrement(24);

        JButton cancelButton = createSecondaryButton("Cancel");

        panel.add(centerScroll, BorderLayout.CENTER);
        panel.add(cancelButton, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        normalizeDemoDialog(dialog);

        Timer factTimer = new Timer(1400, event -> factLabel.setText(toHtml("Fact: " + LoadingScreenFacts.randomFact(), 68)));
        factTimer.start();

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.get();
            }

            @Override
            protected void done() {
                factTimer.stop();
                dialog.dispose();

                if (isCancelled()) {
                    showInfo("Operation cancelled.");
                    return;
                }

                try {
                    T result = get();
                    onSuccess.accept(result);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof CancellationException) {
                        showInfo("Operation cancelled.");
                    } else {
                        showError(cause.getMessage() == null ? "Operation failed." : cause.getMessage());
                    }
                }
            }
        };

        cancelButton.addActionListener(event -> worker.cancel(true));
        worker.execute();
        dialog.setVisible(true);
    }

    /**
     * Opens a URL in the system browser with user-facing error handling.
     */
    private void openInBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            showError("Desktop browsing is not supported on this system.");
            return;
        }

        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            showError("Could not open URL: " + ex.getMessage());
        }
    }

    /**
     * Opens a larger modal view for one related flashcard.
     */
    private void showRelatedFlashcardDialog(Flashcard card, int cardIndex, int totalCards) {
        if (card == null) {
            return;
        }

        JDialog dialog = new JDialog(frame, "Related Flashcard", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(true);
        dialog.setPreferredSize(new Dimension(DEMO_POPUP_MIN_WIDTH, DEMO_POPUP_MIN_HEIGHT));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(COLOR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel indexLabel = new JLabel("Related Flashcard  " + (cardIndex + 1) + " / " + totalCards);
        indexLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        indexLabel.setForeground(COLOR_NAVY);

        JLabel topicLabel = new JLabel("Topic: " + card.getTopic());
        topicLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        topicLabel.setForeground(COLOR_TEXT_DARK);

        JLabel hintLabel = new JLabel("Click the card to flip between prompt and answer.");
        hintLabel.setFont(FONT_HELPER);
        hintLabel.setForeground(COLOR_MID_GRAY);

        top.add(indexLabel);
        top.add(Box.createVerticalStrut(4));
        top.add(topicLabel);
        top.add(Box.createVerticalStrut(2));
        top.add(hintLabel);
        root.add(top, BorderLayout.NORTH);

        JPanel cardPanel = createCardContainer();
        cardPanel.setLayout(new BorderLayout(0, 10));
        cardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel sideLabel = new JLabel("Front");
        sideLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        sideLabel.setForeground(COLOR_NAVY);
        cardPanel.add(sideLabel, BorderLayout.NORTH);

        JTextArea bodyArea = createReadOnlyArea();
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        bodyArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bodyArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JScrollPane bodyScroll = new JScrollPane(
                bodyArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        bodyScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        bodyScroll.getViewport().setBackground(COLOR_CARD);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(18);
        cardPanel.add(bodyScroll, BorderLayout.CENTER);

        final boolean[] showingBack = {false};
        Runnable render = () -> {
            if (showingBack[0]) {
                sideLabel.setText("Back");
                bodyArea.setText(card.getBack());
            } else {
                sideLabel.setText("Front");
                bodyArea.setText(card.getFront());
            }
            bodyArea.setCaretPosition(0);
        };
        render.run();

        MouseAdapter flipHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showingBack[0] = !showingBack[0];
                render.run();
            }
        };
        cardPanel.addMouseListener(flipHandler);
        bodyArea.addMouseListener(flipHandler);
        bodyScroll.getViewport().addMouseListener(flipHandler);

        root.add(cardPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        JButton flipButton = createThemedButton("Flip", COLOR_BTN_TEAL);
        flipButton.addActionListener(e -> {
            showingBack[0] = !showingBack[0];
            render.run();
        });

        JButton closeButton = createThemedButton("Close", COLOR_BTN_SECONDARY);
        closeButton.setForeground(COLOR_TEXT_DARK);
        closeButton.addActionListener(e -> dialog.dispose());

        actions.add(flipButton);
        actions.add(closeButton);
        root.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        normalizeDemoDialog(dialog);
        dialog.setVisible(true);
    }

    /**
     * Creates a labeled field block.
     */
    private JPanel fieldBlock(String labelText, JComponent field) {
        return fieldBlock(labelText, field, false);
    }

    /**
     * Creates a labeled field block and optionally marks label as required.
     */
    private JPanel fieldBlock(String labelText, JComponent field, boolean required) {
        JPanel block = new JPanel();
        block.setBackground(COLOR_WHITE);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(required ? requiredLabelHtml(labelText) : labelText);
        label.setFont(FONT_FIELD_LABEL);
        label.setForeground(COLOR_TEXT_DARK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(label);
        block.add(Box.createVerticalStrut(6));
        block.add(field);
        block.add(Box.createVerticalStrut(12));
        return block;
    }

    /**
     * Wraps a label with inline HTML required marker styling.
     */
    private static String requiredLabelHtml(String text) {
        return "<html>"
                + "<span style='color:#D32F2F'>*</span> "
                + escapeHtml(text)
                + "</html>";
    }

    /**
     * Creates a titled panel wrapper for read and edit widgets.
     */
    private JPanel labeledPanel(String title, JComponent content) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(COLOR_WHITE);
        wrapper.setBorder(BorderFactory.createLineBorder(COLOR_LIGHT_GRAY));

        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(COLOR_TEXT_DARK);
        label.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));

        wrapper.add(label, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Creates a consistent editable text area.
     */
    private JTextArea createEditableArea(int rows) {
        JTextArea area = new JTextArea(rows, 60);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(FONT_BASE);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        return area;
    }

    /**
     * Creates a non-editable text area used for prompts and feedback.
     */
    private JTextArea createReadOnlyArea() {
        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBackground(COLOR_WHITE);
        area.setFont(FONT_BASE);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    /**
     * Creates a primary action button.
     */
    private JButton createPrimaryButton(String text) {
        return createThemedButton(text, COLOR_BTN_PRIMARY);
    }

    /**
     * Creates a secondary action button.
     */
    private JButton createSecondaryButton(String text) {
        return createThemedButton(text, COLOR_BTN_SECONDARY);
    }

    /**
     * Creates a button styled like a link.
     */
    private JButton createLinkButton(String text) {
        JButton button = createThemedButton(text, COLOR_BTN_GHOST);
        button.setForeground(COLOR_NAVY);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setPreferredSize(new Dimension(88, 30));
        return button;
    }

    /**
     * Creates a themed button wrapper with hover and press states.
     */
    private JButton createThemedButton(String text, Color bgColor) {
        Color normalFg;
        if (bgColor.equals(COLOR_BTN_SECONDARY) || bgColor.equals(COLOR_BTN_GHOST)) {
            normalFg = COLOR_TEXT_DARK;
        } else {
            normalFg = Color.WHITE;
        }
        return new ThemedButton(
                text,
                bgColor,
                adjustBrightness(bgColor, 1.08),
                adjustBrightness(bgColor, 0.92),
                COLOR_DISABLED_BG,
                normalFg,
                COLOR_DISABLED_TEXT
        );
    }

    /**
     * Returns a brightness-adjusted color variant.
     */
    private static Color adjustBrightness(Color input, double factor) {
        int r = Math.max(0, Math.min(255, (int) Math.round(input.getRed() * factor)));
        int g = Math.max(0, Math.min(255, (int) Math.round(input.getGreen() * factor)));
        int b = Math.max(0, Math.min(255, (int) Math.round(input.getBlue() * factor)));
        return new Color(r, g, b);
    }

    /**
     * Custom button that paints subtle hover and pressed overlays.
     */
    private static final class ThemedButton extends JButton {

        private Color normalBg;
        private Color hoverBg;
        private Color pressedBg;
        private final Color disabledBg;
        private final Color normalFg;
        private final Color disabledFg;
        private boolean hover;
        private boolean pressed;

        private ThemedButton(
                String text,
                Color normalBg,
                Color hoverBg,
                Color pressedBg,
                Color disabledBg,
                Color normalFg,
                Color disabledFg
        ) {
            super(text);
            this.normalBg = normalBg;
            this.hoverBg = hoverBg;
            this.pressedBg = pressedBg;
            this.disabledBg = disabledBg;
            this.normalFg = normalFg;
            this.disabledFg = disabledFg;

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(FONT_BUTTON);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
            setHorizontalTextPosition(SwingConstants.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!isEnabled()) {
                        return;
                    }
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) {
                        return;
                    }
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
            updateVisualState();
        }

        @Override
        public void setBackground(Color bg) {
            super.setBackground(bg);
            if (bg != null) {
                this.normalBg = bg;
                this.hoverBg = adjustBrightness(bg, 1.08);
                this.pressedBg = adjustBrightness(bg, 0.92);
            }
            repaint();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            updateVisualState();
        }

        private void updateVisualState() {
            if (!isEnabled()) {
                setForeground(disabledFg);
            } else {
                setForeground(normalFg);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill;
            if (!isEnabled()) {
                fill = disabledBg;
            } else if (pressed) {
                fill = pressedBg;
            } else if (hover) {
                fill = hoverBg;
            } else {
                fill = normalBg;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color border = isEnabled() ? adjustBrightness(normalBg, 0.80) : adjustBrightness(disabledBg, 0.82);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
        }
    }

    /**
     * Applies flat button styling used by secondary controls.
     */
    private void styleFlatButton(JButton button, Color foreground, Color border) {
        button.setBackground(COLOR_WHITE);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createLineBorder(border));
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
    }

    /**
     * Loads and scales the app logo from classpath or disk when available.
     */
    private JLabel createLogoLabel(int sizePx) {
        try {
            Image image = loadLogoImage();
            if (image != null) {
                Image scaled = image.getScaledInstance(sizePx, sizePx, Image.SCALE_SMOOTH);
                return new JLabel(new javax.swing.ImageIcon(scaled));
            }
        } catch (Exception ignored) {
        }

        JLabel fallback = new JLabel("CL");
        fallback.setFont(new Font("Segoe UI", Font.BOLD, Math.max(42, sizePx / 3)));
        fallback.setForeground(COLOR_NAVY);
        return fallback;
    }

    /**
     * Loads logo bytes from classpath first, then from common file locations.
     */
    private Image loadLogoImage() {
        for (String resourcePath : LOGO_RESOURCE_CANDIDATES) {
            try (InputStream in = Main.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    continue;
                }
                Image image = ImageIO.read(in);
                if (image != null) {
                    return image;
                }
            } catch (IOException ignored) {
            }
        }

        Set<Path> roots = new LinkedHashSet<>();
        roots.add(Paths.get("").toAbsolutePath().normalize());

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            roots.add(Paths.get(userDir).toAbsolutePath().normalize());
        }

        try {
            URI codeLocation = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codePath = Paths.get(codeLocation).toAbsolutePath().normalize();
            Path appDir = Files.isRegularFile(codePath) ? codePath.getParent() : codePath;
            if (appDir != null) {
                roots.add(appDir);
            }
        } catch (Exception ignored) {
        }

        for (Path root : roots) {
            for (String fileName : LOGO_FILE_CANDIDATES) {
                Path candidate = root.resolve(fileName);
                if (!Files.isRegularFile(candidate)) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(candidate)) {
                    Image image = ImageIO.read(in);
                    if (image != null) {
                        return image;
                    }
                } catch (IOException ignored) {
                }
            }
        }

        return null;
    }

    /**
     * Resets all create-form inputs back to defaults.
     */
    private void clearCreateForm() {
        if (createTitleField != null) {
            createTitleField.setText("");
        }
        if (createSourceArea != null) {
            createSourceArea.setText("");
        }
        if (createTopicGoalsArea != null) {
            createTopicGoalsArea.setText("");
        }
        if (createInstructionsArea != null) {
            createInstructionsArea.setText("");
        }
        if (createFlashcardsSpinner != null) {
            createFlashcardsSpinner.setValue(12);
        }
        if (createDifficultySlider != null) {
            createDifficultySlider.setValue(60);
        }
        if (createChallengeCheck != null) {
            createChallengeCheck.setSelected(true);
        }
    }

    /**
     * Converts nullable text to an empty string.
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Collapses whitespace and truncates text for compact display use.
     */
    private static String compactText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    /**
     * Produces a normalized token key for fuzzy matching and deduplication.
     */
    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replaceAll("[^a-z0-9]", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    /**
     * Checks uniqueness of a candidate option after normalization.
     */
    private static boolean isChoiceUnique(List<String> existing, String candidate) {
        String key = normalizeKey(candidate);
        if (key.isBlank()) {
            return false;
        }
        for (String value : existing) {
            if (normalizeKey(value).equals(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * URL-encodes query text for safe embedding in links.
     */
    private static String encodeQuery(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    /**
     * Wraps plain text in HTML with fixed content width for Swing labels.
     */
    private static String toHtml(String text, int width) {
        return "<html><body style='width:" + width + "em'>" + escapeHtml(text) + "</body></html>";
    }

    /**
     * Escapes minimal HTML entities for safe label rendering.
     */
    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Formats percentage values for UI display.
     */
    private static String formatPercent(double value) {
        if (value < 0.0) {
            return "0%";
        }
        return PERCENT_FORMAT.format(value) + "%";
    }

    /**
     * Clamps numeric values into the inclusive [0,1] range.
     */
    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5;
        }
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }

    /**
     * Throws cancellation when current thread is interrupted.
     */
    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Operation cancelled.");
        }
    }

    /**
     * Shows an informational dialog.
     */
    private void showInfo(String message) {
        showScrollableMessageDialog(message, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows an error dialog.
     */
    private void showError(String message) {
        showScrollableMessageDialog(message, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows a scrollable large-font message dialog for demo visibility.
     */
    private void showScrollableMessageDialog(String message, int messageType) {
        JTextArea area = new JTextArea(nullToEmpty(message), 8, 42);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(FONT_BASE);
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(
                area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(DEMO_POPUP_MIN_WIDTH - 120, DEMO_POPUP_MIN_HEIGHT - 220));
        scroll.getVerticalScrollBar().setUnitIncrement(24);

        JOptionPane pane = new JOptionPane(scroll, messageType, JOptionPane.DEFAULT_OPTION);
        JDialog dialog = pane.createDialog(frame, "ConceptLab");
        normalizeDemoDialog(dialog);
        dialog.setVisible(true);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }

    /**
     * Immutable snapshot of create-form generation settings.
     */
    private static final class GenerationInputs {

        private final String title;
        private final String sourceMaterial;
        private final String topicGoals;
        private final String customInstructions;
        private final int flashcardCount;
        private final int practiceCount;
        private final double targetDifficulty;
        private final boolean includeChallenge;

        private GenerationInputs(
                String title,
                String sourceMaterial,
                String topicGoals,
                String customInstructions,
                int flashcardCount,
                int practiceCount,
                double targetDifficulty,
                boolean includeChallenge
        ) {
            this.title = title;
            this.sourceMaterial = sourceMaterial;
            this.topicGoals = topicGoals;
            this.customInstructions = customInstructions;
            this.flashcardCount = flashcardCount;
            this.practiceCount = practiceCount;
            this.targetDifficulty = targetDifficulty;
            this.includeChallenge = includeChallenge;
        }
    }

    /**
     * Result envelope for answer evaluation responses.
     */
    private static final class AnswerCheckResult {

        private final boolean correct;
        private final String feedback;

        private AnswerCheckResult(boolean correct, String feedback) {
            this.correct = correct;
            this.feedback = feedback == null ? "" : feedback;
        }
    }

    /**
     * Parsed API result used to carry generated title, topic, and flashcards.
     */
    private static final class FlashcardGenerationResult {

        private final String title;
        private final String topic;
        private final List<Flashcard> flashcards;

        private FlashcardGenerationResult(String title, String topic, List<Flashcard> flashcards) {
            this.title = title == null || title.isBlank() ? "Untitled Set" : title.trim();
            this.topic = topic == null || topic.isBlank() ? this.title : topic.trim();
            this.flashcards = flashcards == null ? List.of() : new ArrayList<>(flashcards);
        }
    }

    /**
     * Mutable runtime state for an active quiz attempt.
     */
    private static final class QuizSession {

        private final List<Question> questions;
        private final boolean unitTest;
        private final String title;
        private final int[] chosenIndices;
        private int index;
        private int correctAnswers;
        private boolean answeredThisQuestion;
        private boolean finished;

        private QuizSession(List<Question> questions, boolean unitTest, String title) {
            this.questions = new ArrayList<>(questions);
            this.unitTest = unitTest;
            this.title = title;
            this.chosenIndices = new int[questions.size()];
            for (int i = 0; i < chosenIndices.length; i++) {
                chosenIndices[i] = -1;
            }
        }

        private Question currentQuestion() {
            return questions.get(index);
        }

        private boolean isLastQuestion() {
            return index >= questions.size() - 1;
        }

        private void markAnswer(int chosenIndex, boolean correct) {
            if (answeredThisQuestion) {
                return;
            }
            chosenIndices[index] = chosenIndex;
            answeredThisQuestion = true;
            if (correct) {
                correctAnswers++;
            }
        }

        private void moveNext() {
            index++;
            answeredThisQuestion = false;
        }

        private boolean hasUnsavedProgress() {
            if (finished) {
                return false;
            }
            if (answeredThisQuestion) {
                return true;
            }
            for (int chosen : chosenIndices) {
                if (chosen >= 0) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Lightweight circular percentage indicator for dashboard score display.
     */
    private static final class ProgressRing extends JComponent {

        private static final long serialVersionUID = 1L;
        private double percent;
        private final int ringSize;

        private ProgressRing(double percent) {
            this(percent, 58);
        }

        private ProgressRing(double percent, int ringSize) {
            this.percent = percent;
            this.ringSize = ringSize;
            setPreferredSize(new Dimension(ringSize, ringSize));
        }

        void setPercent(double pct) {
            this.percent = pct;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 8;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(180, 185, 195)); // darker track
            g2.drawOval(x, y, size, size);

            double normalized = Math.max(0.0, Math.min(100.0, percent < 0.0 ? 0.0 : percent));
            if (normalized > 0.0) {
                g2.setColor(COLOR_SUCCESS);
                int angle = (int) Math.round((normalized / 100.0) * 360.0);
                g2.drawArc(x, y, size, size, 90, -angle);
            }
            // Draw percent text centered inside the ring.
            String pctText = percent < 0 ? "N/A" : PERCENT_FORMAT.format(normalized) + "%";
            g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(DEMO_MIN_TEXT_SIZE, ringSize / 5)));
            g2.setColor(COLOR_TEXT_DARK);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(pctText)) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(pctText, tx, ty);

            g2.dispose();
        }
    }

    /**
     * Builds the strict JSON prompt used for API flashcard generation.
     */
    private String buildStudySetGenerationPrompt(GenerationInputs in) {
        return ""
                + "OUT=JSON ONLY; EXACT SCHEMA; NO extra keys; NO md/text; NO null/NaN; NO trailing commas. If any rule fails, FIX INTERNALLY then output final JSON.\n"
                + "MODE: FLASHCARDS_ONLY for initial set generation. practice_questions and unit_test_questions and resources are generated in separate calls.\n"
                + "HARD COUNTS: flashcards.len=fcN EXACT. NEVER output wrong count.\n"
                + "TOPIC TAG: every item has topic=short precise concept (not title).\n"
                + "FLASHCARDS QUALITY: cards must be longer and more in-depth than basic definitions. Back should typically include clear concept explanation, key mechanism, and a concrete worked mini-example or application.\n"
                + "FLASHCARDS ORDER 2 PHASES: A=foundation (~35-55% unless fcN too small) covering core concepts; B=application/error-spot/scenario/method-choice. If fcN allows, B>=45%.\n"
                + "RIGOR: avoid filler; every card teaches one distinct concept with useful detail.\n"
                + "INPUT:title=" + in.title
                + ";src=" + in.sourceMaterial
                + ";goals=" + in.topicGoals
                + ";instr=" + in.customInstructions
                + ";fcN=" + in.flashcardCount
                + ";td=" + in.targetDifficulty
                + ";challFlag=" + in.includeChallenge + ".\n"
                + "INTERNAL PLAN(no output): build concept map 8-18 core prereq->adv + misconceptions + applications; assign topics; decide A vs B cards.\n"
                + "SCHEMA ONLY:\n"
                + "{"
                + "\"title\":string,"
                + "\"topic\":string,"
                + "\"flashcards\":[{\"topic\":string,\"front\":string,\"back\":string}],"
                + "\"practice_questions\":[],"
                + "\"unit_test_questions\":[],"
                + "\"resources\":[]"
                + "}\n";
    }

    /**
     * Builds the strict JSON prompt used for API resource generation.
     */
    private String buildResourcesGenerationPrompt(GenerationInputs in, String topic) {
        return ""
                + "OUT=JSON ONLY; EXACT SCHEMA; NO extra keys; NO md/text; NO null/NaN; NO trailing commas.\n"
                + "MODE: RESOURCES_ONLY generation.\n"
                + "PRIORITY: QUALITY over quantity. Prefer fewer high-confidence working links over many uncertain links.\n"
                + "TARGET COUNT: aim 10..20 resources, but if quality is uncertain output fewer rather than bad links.\n"
                + "HARD LINK QUALITY: links must be real, direct canonical pages, titled correctly, topic-aligned, and unique after normalization.\n"
                + "VALIDITY: triple-check before output. Only output links you are highly confident exist and resolve to the described page. Prefer stable domains: phet.colorado.edu, khanacademy.org, openstax.org, LibreTexts, CK-12, reputable edu domains.\n"
                + "NO SEARCH/ERROR PAGES: do NOT output search-result URLs, category homepages, placeholder links, or likely 404 pages.\n"
                + "NO DUP: no duplicate URL destinations and no near-duplicate links pointing to the same content.\n"
                + "TOPIC TAG: each resource topic is short precise concept label.\n"
                + "INPUT:title=" + in.title
                + ";topic=" + topic
                + ";src=" + in.sourceMaterial
                + ";goals=" + in.topicGoals
                + ";instr=" + in.customInstructions + ".\n"
                + "SCHEMA ONLY:\n"
                + "{"
                + "\"resources\":[{\"topic\":string,\"title\":string,\"type\":\"SIMULATION\"|\"REFERENCE\"|\"ARTICLE\"|\"VIDEO\"|\"INTERACTIVE\"|\"PRACTICE\"|\"OTHER\",\"url\":string}]"
                + "}\n";
    }

    /**
     * Attempts full set generation through API calls and returns null on parse
     * failure.
     */
    @SuppressWarnings("unchecked")
    private StudySet generateStudySetViaApi(GenerationInputs inputs) throws Exception {
        throwIfInterrupted();
        FlashcardGenerationResult cardsResult = generateFlashcardsViaApi(inputs);
        if (cardsResult == null || cardsResult.flashcards.isEmpty()) {
            System.err.println("[ConceptLab] API flashcard generation incomplete. Triggering fallback.");
            return null;
        }

        String title = cardsResult.title;
        String topic = cardsResult.topic;
        List<Flashcard> flashcards = cardsResult.flashcards;

        List<String> facets = extractFacets(inputs.sourceMaterial, inputs.topicGoals, inputs.customInstructions);
        if (facets.isEmpty()) {
            for (Flashcard card : flashcards) {
                facets.add(card.getFront());
                facets.add(card.getBack());
            }
        }

        Set<String> blockedP = new LinkedHashSet<>();
        Set<String> blockedC = new LinkedHashSet<>();

        int unitTarget = determineUnitTestLength(facets, 0);
        List<Question> unitTest = generateQuestionsViaApi(
                topic,
                facets,
                unitTarget,
                0.75,
                true,
                DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT,
                new LinkedHashSet<>(blockedP),
                new LinkedHashSet<>(blockedC),
                true
        );
        if (unitTest == null || unitTest.isEmpty()) {
            unitTest = generateQuestions(
                    facets,
                    unitTarget,
                    0.75,
                    true,
                    DEFAULT_UNITTEST_OPEN_RESPONSE_PERCENT,
                    topic,
                    blockedP,
                    blockedC,
                    true
            );
        }

        List<ResourceLink> resources = generateResourcesViaApi(inputs, topic);
        if (resources.isEmpty()) {
            resources = generateResources(topic, inputs.sourceMaterial);
        }

        if (flashcards.isEmpty() || unitTest.isEmpty()) {
            System.err.println("[ConceptLab] API parse incomplete (empty flashcards or unit test). Triggering fallback.");
            return null;
        }

        return new StudySet(title, flashcards, List.of(), unitTest, resources, -1.0);
    }

    /**
     * Calls API for flashcard JSON and normalizes parsed results.
     */
    @SuppressWarnings("unchecked")
    private FlashcardGenerationResult generateFlashcardsViaApi(GenerationInputs inputs) throws Exception {
        String prompt = buildStudySetGenerationPrompt(inputs);
        String content = callGroqChat(
                "You are a helpful education assistant that generates study materials. Return ONLY valid JSON.",
                prompt,
                GROQ_TEMPERATURE_STUDY_SET
        );
        String jsonStr = stripMarkdownCodeFence(content);
        if (jsonStr.isBlank()) {
            return null;
        }
        Map<String, Object> json = (Map<String, Object>) new JsonParser(jsonStr).parse();
        String title = jsonStr(json, "title", inputs.title);
        String topic = jsonStr(json, "topic", deriveTopic(inputs.topicGoals, inputs.title));
        List<Flashcard> parsed = parseFlashcardsFromApiJson(jsonList(json, "flashcards"), topic);
        List<String> facets = extractFacets(inputs.sourceMaterial, inputs.topicGoals, inputs.customInstructions);
        List<Flashcard> flashcards = enforceFlashcardCount(parsed, inputs.flashcardCount, topic, facets);
        if (parsed.size() != flashcards.size()) {
            System.err.println("[ConceptLab] Flashcard count normalized from " + parsed.size()
                    + " to " + flashcards.size() + " (requested " + inputs.flashcardCount + ").");
        }
        return new FlashcardGenerationResult(title, topic, flashcards);
    }

    /**
     * Calls API for resource JSON and validates returned links.
     */
    @SuppressWarnings("unchecked")
    private List<ResourceLink> generateResourcesViaApi(GenerationInputs inputs, String topic) throws Exception {
        String prompt = buildResourcesGenerationPrompt(inputs, topic);
        String content = callGroqChat(
                "You are a helpful education assistant that curates high-quality study resources. Return ONLY valid JSON.",
                prompt,
                GROQ_TEMPERATURE_STUDY_SET
        );
        String jsonStr = stripMarkdownCodeFence(content);
        if (jsonStr.isBlank()) {
            return List.of();
        }
        Map<String, Object> json = (Map<String, Object>) new JsonParser(jsonStr).parse();
        return validateAndRepairResources(parseResourcesFromApiJson(jsonList(json, "resources"), topic), topic);
    }

    /**
     * Calls API for question JSON and filters duplicates against blocked sets.
     */
    @SuppressWarnings("unchecked")
    private List<Question> generateQuestionsViaApi(
            String topic, List<String> facets, int count, double difficulty,
            boolean includeChallenge, int openResponsePercent, Set<String> blockedPromptKeys,
            Set<String> blockedCorrectKeys, boolean isUnitTest
    ) throws Exception {
        throwIfInterrupted();

        if (count <= 0) {
            return List.of();
        }

        String setTitle = currentSet != null ? currentSet.getTitle() : topic;
        String sourceMaterial = lastGenerationInputs != null ? lastGenerationInputs.sourceMaterial
                : String.join("\n", facets.subList(0, Math.min(facets.size(), 30)));
        String topicGoals = lastGenerationInputs != null ? lastGenerationInputs.topicGoals : topic;
        String customInstructions = lastGenerationInputs != null ? lastGenerationInputs.customInstructions : "";

        int targetCount = isUnitTest ? Math.min(count, 60) : count;
        List<String> sourceChunks = splitTextForApi(sourceMaterial, GROQ_SOURCE_CHUNK_CHARS);
        List<Question> questions = new ArrayList<>();
        Exception lastBatchFailure = null;
        int requiredBatches = (targetCount + GROQ_QUESTION_BATCH_SIZE - 1) / GROQ_QUESTION_BATCH_SIZE;
        int maxBatchCalls = requiredBatches + 3;

        for (int batchIndex = 0;
                questions.size() < targetCount && batchIndex < maxBatchCalls;
                batchIndex++) {
            throwIfInterrupted();
            int batchCount = Math.min(GROQ_QUESTION_BATCH_SIZE, targetCount - questions.size());
            String batchSource = sourceChunks.get(batchIndex % sourceChunks.size());
            List<String> forbiddenPrompts = recentValues(
                    blockedPromptKeys, GROQ_FORBIDDEN_ITEMS_PER_BATCH);
            List<String> forbiddenCorrectAnswers = recentValues(
                    blockedCorrectKeys, GROQ_FORBIDDEN_ITEMS_PER_BATCH);

            String prompt = buildQuestionsOnlyPrompt(
                    setTitle,
                    batchSource,
                    topicGoals,
                    customInstructions,
                    false,
                    batchCount,
                    batchCount,
                    batchCount,
                    difficulty,
                    includeChallenge,
                    openResponsePercent,
                    forbiddenPrompts,
                    forbiddenCorrectAnswers
            );

            try {
                String content = callGroqChat(
                        "You are a helpful education assistant. Return ONLY valid JSON.",
                        prompt,
                        GROQ_TEMPERATURE_QUESTION_GENERATION);
                String jsonStr = stripMarkdownCodeFence(content);
                Map<String, Object> json = (Map<String, Object>) new JsonParser(jsonStr).parse();
                List<Question> parsed = parseQuestionsFromApiJson(jsonList(json, "questions"), topic);
                if (parsed.size() > batchCount) {
                    parsed = new ArrayList<>(parsed.subList(0, batchCount));
                }
                questions.addAll(filterUnique(parsed, blockedPromptKeys, blockedCorrectKeys));
            } catch (Exception ex) {
                lastBatchFailure = ex;
                System.err.println("[ConceptLab] Question API batch " + (batchIndex + 1)
                        + " failed: " + ex.getMessage());
                continue;
            }
        }

        if (questions.isEmpty() && lastBatchFailure != null) {
            throw lastBatchFailure;
        }
        if (questions.size() > targetCount) {
            return new ArrayList<>(questions.subList(0, targetCount));
        }
        return questions;
    }

    /**
     * Splits large source text into bounded prompt segments without cutting a
     * word when a nearby boundary is available.
     */
    private static List<String> splitTextForApi(String text, int maxChars) {
        String safeText = text == null ? "" : text.trim();
        if (safeText.isEmpty() || safeText.length() <= maxChars) {
            return List.of(safeText);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < safeText.length()) {
            int end = Math.min(safeText.length(), start + maxChars);
            if (end < safeText.length()) {
                int boundary = safeText.lastIndexOf('\n', end);
                if (boundary < start + maxChars / 2) {
                    boundary = safeText.lastIndexOf(' ', end);
                }
                if (boundary >= start + maxChars / 2) {
                    end = boundary;
                }
            }
            String chunk = safeText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end;
            while (start < safeText.length() && Character.isWhitespace(safeText.charAt(start))) {
                start++;
            }
        }
        return chunks.isEmpty() ? List.of("") : chunks;
    }

    /**
     * Returns the newest bounded subset from a set used for prompt-level
     * duplicate avoidance.
     */
    private static List<String> recentValues(Set<String> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> all = new ArrayList<>(values);
        int from = Math.max(0, all.size() - limit);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    @SuppressWarnings("unchecked")
    /**
     * Evaluates an answer using API response with deterministic local fallback.
     */
    private AnswerCheckResult evaluateAnswerViaApi(Question question, String userAnswer, Integer selectedIndex) throws Exception {
        throwIfInterrupted();

        String prompt = buildAnswerCheckPrompt(question, userAnswer);
        try {
            String content = callGroqChat(
                    "You are a strict but supportive tutor. Return ONLY valid JSON.",
                    prompt,
                    GROQ_TEMPERATURE_ANSWER_CHECK
            );
            String jsonStr = stripMarkdownCodeFence(content);
            if (jsonStr.isBlank()) {
                return fallbackAnswerCheck(question, userAnswer, selectedIndex);
            }

            Map<String, Object> json = (Map<String, Object>) new JsonParser(jsonStr).parse();
            boolean defaultCorrect = question.isMultipleChoice() && selectedIndex != null
                    ? question.checkAnswer(selectedIndex)
                    : false;
            boolean correct = jsonBool(json, "correct", defaultCorrect);
            String feedback = jsonStr(json, "feedback", "");
            if (feedback.isBlank()) {
                feedback = jsonStr(json, "explanation", "");
            }
            if (feedback.isBlank()) {
                String next = jsonStr(json, "next_step", "");
                feedback = next.isBlank() ? "" : ("Next step: " + next);
            }
            if (feedback.isBlank()) {
                return fallbackAnswerCheck(question, userAnswer, selectedIndex);
            }
            return new AnswerCheckResult(correct, feedback);
        } catch (Exception ex) {
            System.err.println("[ConceptLab] API answer check failed, using fallback: " + ex.getMessage());
            return fallbackAnswerCheck(question, userAnswer, selectedIndex);
        }
    }

    /**
     * Local answer checker used when API grading is unavailable.
     */
    private AnswerCheckResult fallbackAnswerCheck(Question question, String userAnswer, Integer selectedIndex) {
        boolean correct;
        if (question.isMultipleChoice() && selectedIndex != null) {
            correct = question.checkAnswer(selectedIndex);
        } else {
            correct = !question.getAnswerKey().isBlank() && question.checkAnswerText(userAnswer);
        }

        StringBuilder feedback = new StringBuilder();
        if (!question.isMultipleChoice() && question.getAnswerKey().isBlank()) {
            feedback.append("Automatic grading is currently unavailable for this short-answer response. ")
                    .append("Please review the guidance below and try to refine your reasoning.");
        } else if (correct) {
            feedback.append("Correct. Your response matches the key concept and reasoning for this question.");
        } else {
            feedback.append("Not correct yet. Your response misses at least one required concept or reasoning step.");
        }

        if (question.isMultipleChoice() && selectedIndex != null) {
            String legacy = question.feedbackForChoice(selectedIndex);
            if (legacy != null && !legacy.isBlank()) {
                feedback.append("\n\n").append(legacy);
            }
        } else {
            if (!question.getSolution().isBlank()) {
                feedback.append("\n\nReference solution:\n").append(question.getSolution());
            } else if (!question.getAnswerKey().isBlank()) {
                feedback.append("\n\nKey idea to include:\n").append(question.getAnswerKey());
            }
        }

        return new AnswerCheckResult(correct, feedback.toString());
    }

    /**
     * Builds the grading prompt for API-based answer evaluation.
     */
    private String buildAnswerCheckPrompt(Question question, String userAnswer) {
        String type = question.isMultipleChoice() ? "MCQ" : "SHORT_ANSWER";
        String choicesLine = question.isMultipleChoice()
                ? (";choices=" + String.join(" || ", question.getChoices()) + ";correct_index=" + question.getCorrectIndex())
                : ";choices=(none)";
        String referenceLine = question.isMultipleChoice()
                ? (";answer_key=" + question.getAnswerKey() + ";reference_solution=" + question.getSolution())
                : ";answer_key=(not provided for short-answer)";
        String sourceLine = (lastGenerationInputs != null && lastGenerationInputs.sourceMaterial != null)
                ? ";source_material=" + lastGenerationInputs.sourceMaterial
                : "";
        String goalsLine = (lastGenerationInputs != null && lastGenerationInputs.topicGoals != null)
                ? ";topic_goals=" + lastGenerationInputs.topicGoals
                : "";
        String instructionsLine = (lastGenerationInputs != null && lastGenerationInputs.customInstructions != null)
                ? ";custom_instructions=" + lastGenerationInputs.customInstructions
                : "";

        return ""
                + "OUT=JSON ONLY; EXACT SCHEMA; NO md/text; NO extra keys; NO null/NaN; NO trailing commas.\n"
                + "TASK: evaluate the user answer and return personalized, detailed step-by-step feedback.\n"
                + "QUALITY: be precise, concept-correct, and explicit about reasoning. If incorrect, state exactly where reasoning failed and how to fix it.\n"
                + "STYLE: concise but detailed enough to teach. Include concrete next-step advice and improvement guidance.\n"
                + "CORRECTNESS: return correct boolean by your own judgment. Do not be overly harsh on typos, rounding differences, or minor sig-fig issues unless the prompt explicitly tests those details.\n"
                + "TOLERANCE: if conceptual reasoning is strong and the final value is close with small arithmetic/rounding drift, prefer partial leniency and explain exactly what to improve.\n"
                + "INPUT:topic=" + question.getTopic()
                + ";type=" + type
                + ";prompt=" + question.getPrompt()
                + choicesLine
                + referenceLine
                + sourceLine
                + goalsLine
                + instructionsLine
                + ";user_answer=" + userAnswer + ".\n"
                + "OUTPUT SCHEMA:{\"correct\":boolean,\"feedback\":string}\n";
    }

    /**
     * Sends a single-model chat request through configured Groq endpoint.
     */
    @SuppressWarnings("unchecked")
    private String callGroqChat(String systemMessage, String userMessage, double temperature) throws Exception {
        throwIfInterrupted();

        List<String> keys = new ArrayList<>();
        if (GROQ_API_KEY_PRIMARY != null && !GROQ_API_KEY_PRIMARY.isBlank()) {
            keys.add(GROQ_API_KEY_PRIMARY.trim());
        }
        if (GROQ_API_KEY_SECONDARY != null && !GROQ_API_KEY_SECONDARY.isBlank()) {
            String trimmed = GROQ_API_KEY_SECONDARY.trim();
            if (keys.stream().noneMatch(existing -> existing.equals(trimmed))) {
                keys.add(trimmed);
            }
        }
        if (keys.isEmpty()) {
            throw new IOException(
                    "Groq API key not configured. Set GROQ_API_KEY_PRIMARY"
                    + " (and optionally GROQ_API_KEY_SECONDARY)."
            );
        }

        System.err.println("[ConceptLab][API] call start");
        System.err.println("[ConceptLab][API] prompt chars=" + userMessage.length());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        List<ApiCombo> combinations = new ArrayList<>();
        String primaryKey = keys.get(0);
        combinations.add(new ApiCombo(primaryKey, GROQ_MODEL_OPENAI));
        combinations.add(new ApiCombo(primaryKey, GROQ_MODEL_FALLBACK));
        if (keys.size() > 1) {
            String secondaryKey = keys.get(1);
            combinations.add(new ApiCombo(secondaryKey, GROQ_MODEL_OPENAI));
            combinations.add(new ApiCombo(secondaryKey, GROQ_MODEL_FALLBACK));
        }

        ApiCallException lastEx = null;
        for (int i = 0; i < combinations.size(); i++) {
            ApiCombo combo = combinations.get(i);
            try {
                return callGroqCombo(
                        client,
                        combo.apiKey,
                        combo.model,
                        systemMessage,
                        userMessage,
                        temperature,
                        GROQ_ATTEMPTS_PER_COMBINATION,
                        "combination " + (i + 1) + "/" + combinations.size()
                );
            } catch (ApiCallException ex) {
                lastEx = ex;
                System.err.println("[ConceptLab][API] combination failed. model=" + combo.model
                        + " status=" + ex.statusCode
                        + " tokenRelated=" + ex.tokenRelated
                        + " reason=" + ex.getMessage());
            }
        }

        int totalAttempts = combinations.size() * GROQ_ATTEMPTS_PER_COMBINATION;
        throw new IOException("API call failed after all " + totalAttempts + " key/model attempts.", lastEx);
    }

    /**
     * Sends chat requests with primary and backup API keys and model fallback
     * logic.
     */
    @SuppressWarnings("unchecked")
    private String callGroqCombo(
            HttpClient client,
            String apiKey,
            String model,
            String systemMessage,
            String userMessage,
            double temperature,
            int maxAttempts,
            String phase
    ) throws ApiCallException, CancellationException {
        ApiCallException lastEx = null;
        int requestMaxTokens = calculateGroqMaxOutputTokens(systemMessage, userMessage);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            throwIfInterrupted();
            try {
                String reqBody = "{\"model\":\"" + escJsonStr(model) + "\","
                        + "\"temperature\":" + temperature + ","
                        + "\"max_tokens\":" + requestMaxTokens + ","
                        + "\"reasoning_effort\":\"low\","
                        + "\"response_format\":{\"type\":\"json_object\"},"
                        + "\"messages\":["
                        + "{\"role\":\"system\",\"content\":\"" + escJsonStr(systemMessage) + "\"},"
                        + "{\"role\":\"user\",\"content\":\"" + escJsonStr(userMessage) + "\"}"
                        + "]}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody, StandardCharsets.UTF_8))
                        .build();

                System.err.println("[ConceptLab][API] " + phase
                        + " model=" + model
                        + " attempt " + (attempt + 1) + "/" + maxAttempts
                        + " maxOutputTokens=" + requestMaxTokens);
                long t0 = System.currentTimeMillis();
                HttpResponse<String> resp = client.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long elapsed = System.currentTimeMillis() - t0;
                System.err.println("[ConceptLab][API] HTTP " + resp.statusCode() + " in " + elapsed + "ms");

                if (resp.statusCode() == 200) {
                    Map<String, Object> rJson;
                    try {
                        Object parsedResponse = new JsonParser(resp.body()).parse();
                        if (!(parsedResponse instanceof Map)) {
                            throw new RuntimeException("Expected a JSON response object");
                        }
                        rJson = (Map<String, Object>) parsedResponse;
                    } catch (RuntimeException parseEx) {
                        throw new ApiCallException(
                                "API response envelope was not valid JSON: " + parseEx.getMessage(),
                                false,
                                200,
                                parseEx
                        );
                    }
                    List<Object> choices = (List<Object>) rJson.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> first = (Map<String, Object>) choices.get(0);
                        String finishReason = jsonStr(first, "finish_reason", "");
                        if ("length".equalsIgnoreCase(finishReason)) {
                            throw new ApiCallException(
                                    "API response was truncated at the output-token limit",
                                    true,
                                    200,
                                    null
                            );
                        }
                        Map<String, Object> msg = (Map<String, Object>) first.get("message");
                        String content = msg == null ? "" : jsonStr(msg, "content", "");
                        System.err.println("[ConceptLab][API] response chars=" + (content != null ? content.length() : 0));
                        Object usage = rJson.get("usage");
                        if (usage instanceof Map) {
                            Map<String, Object> u = (Map<String, Object>) usage;
                            System.err.println("[ConceptLab][API] tokens prompt=" + u.get("prompt_tokens")
                                    + " completion=" + u.get("completion_tokens")
                                    + " total=" + u.get("total_tokens"));
                        }
                        if (content == null || content.isBlank()) {
                            throw new ApiCallException("API response content was blank", true, 200, null);
                        }
                        try {
                            Object parsed = new JsonParser(stripMarkdownCodeFence(content)).parse();
                            if (!(parsed instanceof Map)) {
                                throw new RuntimeException("Expected a JSON object");
                            }
                        } catch (RuntimeException parseEx) {
                            throw new ApiCallException(
                                    "API response was not valid JSON: " + parseEx.getMessage(),
                                    false,
                                    200,
                                    parseEx
                            );
                        }
                        return content;
                    }
                    throw new ApiCallException("No choices in API response", false, 200, null);
                }

                String bodySnippet = resp.body() == null
                        ? ""
                        : resp.body().substring(0, Math.min(500, resp.body().length()));
                System.err.println("[ConceptLab][API] non-200 body snippet: " + bodySnippet);
                boolean tokenRelated = isTokenRelatedFailure(resp.statusCode(), bodySnippet);
                long retryDelayMillis = groqRetryDelayMillis(resp, attempt);
                lastEx = new ApiCallException(
                        "API error: HTTP " + resp.statusCode(),
                        tokenRelated,
                        resp.statusCode(),
                        retryDelayMillis,
                        null
                );
                if (resp.statusCode() == 413 && requestMaxTokens > GROQ_MIN_OUTPUT_TOKENS) {
                    requestMaxTokens = Math.max(GROQ_MIN_OUTPUT_TOKENS, requestMaxTokens / 2);
                }
                throw lastEx;
            } catch (ApiCallException ex) {
                lastEx = ex;
                if (attempt + 1 < maxAttempts) {
                    if (ex.statusCode >= 500 || ex.statusCode == 408 || ex.statusCode == 429) {
                        sleepForGroqRetry(ex.retryDelayMillis, attempt);
                    }
                    continue;
                }
                throw ex;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Cancelled");
            } catch (IOException ex) {
                boolean tokenRelated = isTokenRelatedFailure(0, ex.getMessage());
                lastEx = new ApiCallException("API io error: " + ex.getMessage(), tokenRelated, 0, ex);
                System.err.println("[ConceptLab][API] io error model=" + model
                        + " tokenRelated=" + tokenRelated
                        + " msg=" + ex.getMessage());
                if (attempt + 1 < maxAttempts) {
                    sleepForGroqRetry(0L, attempt);
                    continue;
                }
                throw lastEx;
            }
        }

        throw lastEx != null ? lastEx : new ApiCallException("API combo failed.", false, 0, null);
    }

    /**
     * Chooses an output allowance that keeps the estimated request below the
     * current free-tier combined token-per-minute ceiling.
     */
    private static int calculateGroqMaxOutputTokens(String systemMessage, String userMessage) {
        int promptChars = nullToEmpty(systemMessage).length() + nullToEmpty(userMessage).length();
        int estimatedInputTokens = (int) Math.ceil(promptChars / 2.5) + 200;
        int available = GROQ_TPM_BUDGET - GROQ_TOKEN_SAFETY_MARGIN - estimatedInputTokens;
        return Math.max(GROQ_MIN_OUTPUT_TOKENS, Math.min(GROQ_MAX_OUTPUT_TOKENS, available));
    }

    /**
     * Reads Groq's server-provided retry timing, falling back to a short
     * exponential delay when headers are unavailable.
     */
    private static long groqRetryDelayMillis(HttpResponse<?> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("retry-after");
        long parsed = retryAfter.map(Main::parseGroqDurationMillis).orElse(0L);
        if (parsed <= 0L) {
            Optional<String> tokenReset = response.headers().firstValue("x-ratelimit-reset-tokens");
            parsed = tokenReset.map(Main::parseGroqDurationMillis).orElse(0L);
        }
        if (parsed <= 0L) {
            parsed = 1000L * (attempt + 1);
        }
        return Math.min(60_000L, parsed + 250L);
    }

    /**
     * Parses Groq duration headers such as "2", "1.5s", or "1m2.5s".
     */
    private static long parseGroqDurationMillis(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0L;
        }
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        try {
            int minuteIndex = value.indexOf('m');
            double seconds = 0.0;
            if (minuteIndex >= 0) {
                seconds += Double.parseDouble(value.substring(0, minuteIndex)) * 60.0;
                value = value.substring(minuteIndex + 1);
            }
            if (value.endsWith("ms")) {
                seconds += Double.parseDouble(value.substring(0, value.length() - 2)) / 1000.0;
            } else if (value.endsWith("s")) {
                seconds += Double.parseDouble(value.substring(0, value.length() - 1));
            } else if (!value.isBlank()) {
                seconds += Double.parseDouble(value);
            }
            return Math.max(0L, (long) Math.ceil(seconds * 1000.0));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * Sleeps before retrying an API request while preserving cancellation.
     */
    private static void sleepForGroqRetry(long requestedDelayMillis, int attempt) throws CancellationException {
        long delay = requestedDelayMillis > 0L ? requestedDelayMillis : 1000L * (attempt + 1);
        try {
            Thread.sleep(Math.min(60_000L, delay));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Cancelled");
        }
    }

    /**
     * Detects token and quota failures that should trigger key/model fallback.
     */
    private static boolean isTokenRelatedFailure(int statusCode, String detail) {
        String msg = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (statusCode == 413) {
            return true;
        }
        if (statusCode == 429 && msg.isBlank()) {
            return true;
        }
        return msg.contains("token")
                || msg.contains("max_tokens")
                || msg.contains("maximum context")
                || msg.contains("context length")
                || msg.contains("too many tokens")
                || msg.contains("insufficient_quota")
                || msg.contains("quota")
                || msg.contains("rate_limit")
                || msg.contains("rate limit")
                || msg.contains("credit")
                || msg.contains("billing");
    }

    /**
     * Candidate pair of API key and model name used in fallback sequences.
     */
    private static final class ApiCombo {

        private final String apiKey;
        private final String model;

        private ApiCombo(String apiKey, String model) {
            this.apiKey = apiKey;
            this.model = model;
        }
    }

    /**
     * API exception that carries retry-related metadata.
     */
    private static final class ApiCallException extends IOException {

        private final boolean tokenRelated;
        private final int statusCode;
        private final long retryDelayMillis;

        private ApiCallException(String message, boolean tokenRelated, int statusCode, Throwable cause) {
            this(message, tokenRelated, statusCode, 0L, cause);
        }

        private ApiCallException(
                String message,
                boolean tokenRelated,
                int statusCode,
                long retryDelayMillis,
                Throwable cause
        ) {
            super(message, cause);
            this.tokenRelated = tokenRelated;
            this.statusCode = statusCode;
            this.retryDelayMillis = retryDelayMillis;
        }
    }

    /**
     * Builds a strict "questions-only" prompt for ConceptLab.
     *
     * Supports TWO modes: - Fixed-count mode (typical "Generate New Quiz"): set
     * chooseCountForCoverage=false and provide exactCount. - Coverage-based
     * mode (unit test regeneration): set chooseCountForCoverage=true and
     * provide minCount/maxCount.
     *
     * Uniqueness: - Provide forbiddenPrompts + forbiddenCorrectAnswers (from
     * existing banks) to prevent repeats. - The model is instructed to avoid
     * paraphrase-duplicates too.
     *
     * Output schema (JSON only): { "questions": [ {topic, response_type,
     * prompt, choices, correct_index, difficulty, challenge}, ... ] }
     */
    private String buildQuestionsOnlyPrompt(
            String setTitle,
            String sourceMaterial,
            String topicGoals,
            String customInstructions,
            boolean chooseCountForCoverage,
            int exactCount,
            int minCount,
            int maxCount,
            double targetDifficulty,
            boolean includeChallenge,
            int openResponsePercent,
            java.util.List<String> forbiddenPrompts,
            java.util.List<String> forbiddenCorrectAnswers
    ) {
        String safeTitle = (setTitle == null) ? "" : setTitle;
        String safeSource = (sourceMaterial == null) ? "" : sourceMaterial;
        String safeGoals = (topicGoals == null) ? "" : topicGoals;
        String safeInstr = (customInstructions == null) ? "" : customInstructions;

        java.util.List<String> fp = (forbiddenPrompts == null) ? java.util.Collections.emptyList() : forbiddenPrompts;
        java.util.List<String> fa = (forbiddenCorrectAnswers == null) ? java.util.Collections.emptyList() : forbiddenCorrectAnswers;

        int fpCap = Math.min(fp.size(), 120);
        int faCap = Math.min(fa.size(), 120);

        StringBuilder forbiddenPromptBlock = new StringBuilder();
        for (int i = 0; i < fpCap; i++) {
            forbiddenPromptBlock.append("- ").append(fp.get(i)).append("\n");
        }

        StringBuilder forbiddenAnswerBlock = new StringBuilder();
        for (int i = 0; i < faCap; i++) {
            forbiddenAnswerBlock.append("- ").append(fa.get(i)).append("\n");
        }

        String countRule = chooseCountForCoverage
                ? ("HARD COUNT: questions.len in [" + minCount + ".." + maxCount + "]; choose by coverage; cover all core concepts; no bloat.\n")
                : ("HARD COUNT: questions.len=" + exactCount + " EXACT; NEVER output wrong count.\n");

        return ""
                + "OUT=JSON ONLY; EXACT SCHEMA; NO md/text; NO extra keys; NO null/NaN; NO trailing commas. If any rule fails, FIX INTERNALLY then output final JSON.\n"
                + countRule
                + "HARD DIFF: avg(difficulty) within td+/-0.04 and spread: >=20% diff>=0.80, >=20% diff<=0.55 (unless N<10). Low diff still APPLY (at least one real reasoning/compute step).\n"
                + "HARD CHALL: if challFlag=true then around 20-35% challenge=true; else all challenge=false.\n"
                + "HARD APPLY: >=85% APPLY (compute/infer/interpret/method-choice/error-spot). defs only allowed as vocab-in-context.\n"
                + "MIXED TYPES: allow BOTH MCQ and SHORT_ANSWER items. Use SHORT_ANSWER when it better tests reasoning.\n"
                + "TYPE MIX TARGET: open_response_target_pct=" + openResponsePercent + " (approximate, reasonable spread).\n"
                + "MCQ RULES: exactly 4 choices and exactly 1 defensible correct; solve + recheck; if ambiguous, rewrite.\n"
                + "SHORT_ANSWER RULES: do NOT provide answer_key for short-answer items; the app will grade at answer-time.\n"
                + "VARIETY: mix compute, inference, graph/table interpretation, method-choice, and error diagnosis; avoid repeating the same question skeleton.\n"
                + "TOPIC TAG: each Q topic=short precise concept.\n"
                + "GENERATION EFFICIENCY: do NOT output per-choice feedback arrays. The app will generate personalized feedback at answer-time.\n"
                + "SIGFIG: phys/chem correct option has correct sig figs from lowest-sf givens (assume '10'=1sf); do NOT instruct sf. Math rounding: state in prompt only if needed.\n"
                + "NO DUP: do NOT reuse/paraphrase forbidden prompts; treat number swaps as dup; avoid repeating same skeleton; avoid repeating same correct-answer text.\n"
                + "INPUT:title=" + safeTitle + ";src=" + safeSource + ";goals=" + safeGoals + ";instr=" + safeInstr
                + ";td=" + targetDifficulty + ";challFlag=" + includeChallenge + ".\n"
                + "INTERNAL PLAN(no output): build concept map 8-18 core prereq->adv + misconceptions + applications; assign target difficulties first; draft full unique question list; then output final JSON.\n"
                + "FORBIDDEN PROMPTS:\n" + (fpCap == 0 ? "-(none)\n" : forbiddenPromptBlock.toString())
                + "FORBIDDEN CORRECT TEXT:\n" + (faCap == 0 ? "-(none)\n" : forbiddenAnswerBlock.toString())
                + "SCHEMA ONLY:{\"questions\":[{\"topic\":string,\"response_type\":\"MCQ\"|\"SHORT_ANSWER\",\"prompt\":string,\"choices\":[string,string,string,string]|[],\"correct_index\":0-3|-1,\"difficulty\":number,\"challenge\":boolean}]}\n";
    }

    @SuppressWarnings("unchecked")
    /**
     * Parses flashcard objects from API JSON payload.
     */
    private List<Flashcard> parseFlashcardsFromApiJson(List<Object> array, String defaultTopic) {
        List<Flashcard> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (Object item : array) {
            try {
                Map<String, Object> obj = (Map<String, Object>) item;
                String t = jsonStr(obj, "topic", defaultTopic);
                String front = jsonStr(obj, "front", "");
                String back = jsonStr(obj, "back", "");
                if (!front.isBlank() && !back.isBlank()) {
                    result.add(new Flashcard(t, front, back));
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    /**
     * Parses question objects from API JSON payload.
     */
    private List<Question> parseQuestionsFromApiJson(List<Object> array, String defaultTopic) {
        List<Question> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (Object item : array) {
            try {
                Map<String, Object> obj = (Map<String, Object>) item;
                String t = jsonStr(obj, "topic", defaultTopic);
                String prompt = jsonStr(obj, "prompt", "");
                if (prompt.isBlank()) {
                    continue;
                }

                double diff = jsonDouble(obj, "difficulty", 0.5);
                if (diff < 0.0 || diff > 1.0) {
                    diff = 0.5;
                }

                boolean challenge = jsonBool(obj, "challenge", false);
                String responseType = jsonStr(obj, "response_type", "MCQ");
                String solution = jsonStr(obj, "solution", "");

                List<Object> choicesList = jsonList(obj, "choices");
                boolean wantsShort = "SHORT_ANSWER".equalsIgnoreCase(responseType)
                        || "SHORT".equalsIgnoreCase(responseType)
                        || choicesList == null
                        || choicesList.isEmpty();

                if (wantsShort) {
                    String answerKey = jsonStr(obj, "answer_key", "");
                    if (answerKey.isBlank()) {
                        answerKey = jsonStr(obj, "expected_answer", "");
                    }
                    result.add(new Question(
                            null,
                            t,
                            prompt,
                            Question.ResponseType.SHORT_ANSWER,
                            new String[0],
                            -1,
                            new String[0],
                            answerKey,
                            solution,
                            diff,
                            challenge
                    ));
                    continue;
                }

                if (choicesList.size() != 4) {
                    continue;
                }

                String[] choices = new String[4];
                for (int i = 0; i < 4; i++) {
                    choices[i] = String.valueOf(choicesList.get(i)).trim();
                    if (choices[i].isBlank()) {
                        choices[i] = "Option " + (char) ('A' + i);
                    }
                }

                boolean dupChoice = false;
                for (int i = 0; i < 4 && !dupChoice; i++) {
                    for (int j = i + 1; j < 4 && !dupChoice; j++) {
                        if (normalizeKey(choices[i]).equals(normalizeKey(choices[j]))) {
                            dupChoice = true;
                        }
                    }
                }
                if (dupChoice) {
                    continue;
                }

                int ci = jsonInt(obj, "correct_index", 0);
                if (ci < 0 || ci > 3) {
                    ci = 0;
                }

                List<Object> fbList = jsonList(obj, "feedback");
                String[] feedback = new String[4];
                for (int i = 0; i < 4; i++) {
                    feedback[i] = (fbList != null && i < fbList.size())
                            ? String.valueOf(fbList.get(i)).trim()
                            : "";
                }

                String answerKey = jsonStr(obj, "answer_key", "");
                if (answerKey.isBlank() && ci >= 0 && ci < choices.length) {
                    answerKey = choices[ci];
                }

                result.add(new Question(
                        null,
                        t,
                        prompt,
                        Question.ResponseType.MCQ,
                        choices,
                        ci,
                        feedback,
                        answerKey,
                        solution,
                        diff,
                        challenge
                ));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    /**
     * Parses resource objects from API JSON payload.
     */
    private List<ResourceLink> parseResourcesFromApiJson(List<Object> array, String defaultTopic) {
        List<ResourceLink> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (Object item : array) {
            try {
                Map<String, Object> obj = (Map<String, Object>) item;
                String t = jsonStr(obj, "topic", defaultTopic);
                String title = jsonStr(obj, "title", "");
                String typeStr = jsonStr(obj, "type", "REFERENCE");
                String url = jsonStr(obj, "url", "");
                if (title.isBlank() || url.isBlank()) {
                    continue;
                }
                ResourceType type;
                try {
                    type = ResourceType.valueOf(typeStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    type = ResourceType.REFERENCE;
                }
                result.add(new ResourceLink(t, title, type, url));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * Filters duplicate questions against blocked prompt and answer keys.
     */
    private static List<Question> filterUnique(
            List<Question> questions, Set<String> blockedPrompts, Set<String> blockedCorrect
    ) {
        List<Question> filtered = new ArrayList<>();
        for (Question q : questions) {
            String pk = q.normalizedPromptKey();
            String ck = q.normalizedCorrectAnswerKey();
            if (blockedPrompts.contains(pk)) {
                continue;
            }
            if (!ck.isBlank() && blockedCorrect.contains(ck)) {
                continue;
            }
            filtered.add(q);
            blockedPrompts.add(pk);
            if (!ck.isBlank()) {
                blockedCorrect.add(ck);
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    /**
     * Reads a string value from parsed JSON map with default fallback.
     */
    private static String jsonStr(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        if (v instanceof String s) {
            return s;
        }
        if (v != null) {
            return String.valueOf(v);
        }
        return defaultVal;
    }

    /**
     * Reads an integer value from parsed JSON map with default fallback.
     */
    private static int jsonInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    /**
     * Reads a double value from parsed JSON map with default fallback.
     */
    private static double jsonDouble(Map<String, Object> map, String key, double defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    /**
     * Reads a boolean value from parsed JSON map with default fallback.
     */
    private static boolean jsonBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    /**
     * Reads a list value from parsed JSON map with safe empty-list fallback.
     */
    private static List<Object> jsonList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        return null;
    }

    /**
     * Escapes text for manual JSON string composition.
     */
    private static String escJsonStr(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' ->
                    sb.append("\\\"");
                case '\\' ->
                    sb.append("\\\\");
                case '\n' ->
                    sb.append("\\n");
                case '\r' ->
                    sb.append("\\r");
                case '\t' ->
                    sb.append("\\t");
                case '\b' ->
                    sb.append("\\b");
                case '\f' ->
                    sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Removes markdown code fences from model output before JSON parsing.
     */
    private static String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    // Minimal recursive-descent JSON parser with no external dependencies.
    private static final class JsonParser {

        private final String input;
        private int pos;

        /**
         * Creates a parser over raw JSON text.
         */
        JsonParser(String input) {
            this.input = Objects.requireNonNull(input, "JSON input must not be null");
            this.pos = 0;
        }

        /**
         * Parses one complete JSON value.
         */
        Object parse() {
            skipWs();
            Object result = value();
            skipWs();
            return result;
        }

        /**
         * Parses any JSON value based on the next non-whitespace token.
         */
        private Object value() {
            skipWs();
            if (pos >= input.length()) {
                throw err("Unexpected end of input");
            }
            char c = input.charAt(pos);
            return switch (c) {
                case '{' ->
                    object();
                case '[' ->
                    array();
                case '"' ->
                    string();
                case 't', 'f' ->
                    bool();
                case 'n' ->
                    nil();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw err("Unexpected character: " + c);
                }
            };
        }

        /**
         * Parses a JSON object.
         */
        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> map = new HashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                Object val = value();
                map.put(key, val);
                skipWs();
                if (peek() == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            expect('}');
            return map;
        }

        /**
         * Parses a JSON array.
         */
        private List<Object> array() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWs();
                if (peek() == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            expect(']');
            return list;
        }

    /**
     * Parses a JSON string including escape sequences.
     */
    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= input.length()) {
                    throw err("Escape at end of string");
                }
                char e = input.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > input.length()) {
                            throw err("Incomplete unicode escape");
                        }
                        sb.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> {
                        sb.append('\\');
                        sb.append(e);
                    }
                }
            } else {
                sb.append(c);
            }
        }
        throw err("Unterminated string");
    }

        /**
         * Parses integer and floating-point number literals.
         */
        private Number number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            digits();
            boolean isFloat = false;
            if (pos < input.length() && input.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                digits();
            }
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                digits();
            }
            String s = input.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(s);
            }
            long val = Long.parseLong(s);
            return (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) ? (int) val : val;
        }

        /**
         * Consumes a contiguous run of numeric digits.
         */
        private void digits() {
            while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
                pos++;
            }
        }

        /**
         * Parses true or false.
         */
        private Boolean bool() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw err("Expected boolean");
        }

        /**
         * Parses null literal.
         */
        private Object nil() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw err("Expected null");
        }

        /**
         * Skips whitespace from current parser position.
         */
        private void skipWs() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        /**
         * Returns current character or zero when input is exhausted.
         */
        private char peek() {
            return pos < input.length() ? input.charAt(pos) : 0;
        }

        /**
         * Verifies and consumes an expected punctuation token.
         */
        private void expect(char c) {
            skipWs();
            if (pos >= input.length() || input.charAt(pos) != c) {
                throw err("Expected '" + c + "'");
            }
            pos++;
        }

        /**
         * Builds a parser error with surrounding context for diagnostics.
         */
        private RuntimeException err(String msg) {
            int contextStart = Math.max(0, pos - 20);
            int contextEnd = Math.min(input.length(), pos + 20);
            String context = input.substring(contextStart, contextEnd);
            return new RuntimeException("JSON parse error at position " + pos + ": " + msg + " near: " + context);
        }
    }
}
