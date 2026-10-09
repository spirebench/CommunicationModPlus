package communicationmodplus;

import com.google.gson.Gson;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tells the agent the game has crashed, before the game shuts down (bug #182).
 *
 * CardCrawlGame catches any exception out of create() or render() -- the whole
 * game loop -- logs it and calls Gdx.app.exit(). The JVM does not always go
 * with it (a crashed headless game can sit in shutdown indefinitely), and the
 * agent, waiting for a state, used to find out only when its 900 s output
 * timeout fired. CardCrawlGameCrashPatch routes those two catch blocks through
 * report(), which publishes a marker in place of a state:
 * {"game_crashed": true, "where": ..., "error": ..., "stack_trace": ...}.
 */
public class GameCrashReporter {
    private static final Logger logger = LogManager.getLogger((String)GameCrashReporter.class.getName());
    private static final int MAX_TRACE_CHARS = 8000;
    private static volatile boolean reported = false;

    public static void report(Throwable e, String where) {
        if (reported) {
            return;
        }
        reported = true;
        try {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            String stackTrace = trace.toString();
            if (stackTrace.length() > MAX_TRACE_CHARS) {
                stackTrace = stackTrace.substring(0, MAX_TRACE_CHARS) + "\n\t...";
            }
            Map<String, Object> marker = new LinkedHashMap<String, Object>();
            marker.put("game_crashed", true);
            marker.put("where", where);
            marker.put("error", e.toString());
            marker.put("stack_trace", stackTrace);
            marker.put("ready_for_command", false);
            marker.put("in_game", false);
            FileDataWriter.publishCrashMarker(new Gson().toJson(marker));
            logger.error("Game crashed in " + where + "; crash marker published to the agent");
        }
        catch (Throwable t) {
            // Never let the report itself change how the game goes down.
            logger.error("Could not report the crash: " + t);
        }
    }
}
