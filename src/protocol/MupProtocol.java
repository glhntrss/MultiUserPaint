package protocol;

public class MupProtocol {

    public static final String JOIN = "JOIN";
    public static final String WELCOME = "WELCOME";
    public static final String REJECT = "REJECT";
    public static final String ERR = "ERR";
    public static final String LEAVE = "LEAVE";

    public static final String LIST = "LIST";
    public static final String FILES = "FILES";

    public static final String CREATE = "CREATE";
    public static final String CREATED = "CREATED";

    public static final String OPEN = "OPEN";
    public static final String OPENED = "OPENED";
    public static final String STATE_BEGIN = "STATE_BEGIN";
    public static final String STATE_OP = "STATE_OP";
    public static final String STATE_END = "STATE_END";

    public static final String DRAW = "DRAW";
    public static final String DRAW_BCAST = "DRAW_BCAST";

    public static final String CRLF = "\r\n";

    private MupProtocol() {
        // Bu sınıf sadece sabitleri tutar.
    }
}