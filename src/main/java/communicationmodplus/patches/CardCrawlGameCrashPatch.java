package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * Report a crash of the game loop to the agent before the game shuts down (bug #182).
 *
 * CardCrawlGame.create() and render() each wrap their body in
 * catch (Exception e) { logger.info(...); ExceptionHandler.handleException(e, logger); Gdx.app.exit(); }.
 * The handleException call in those two methods -- and only there; the game uses it
 * for non-fatal errors elsewhere -- is wrapped so GameCrashReporter sees the
 * exception first. The call itself still runs, unchanged.
 */
public class CardCrawlGameCrashPatch {
    static ExprEditor reportBeforeHandling(final String where) {
        return new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getClassName().equals("com.megacrit.cardcrawl.core.ExceptionHandler")
                        && m.getMethodName().equals("handleException")) {
                    m.replace("{ communicationmodplus.GameCrashReporter.report($1, \"" + where + "\"); $_ = $proceed($$); }");
                }
            }
        };
    }

    @SpirePatch(clz=CardCrawlGame.class, method="render")
    public static class Render {
        @SpireInstrumentPatch
        public static ExprEditor Instrument() {
            return reportBeforeHandling("CardCrawlGame.render");
        }
    }

    @SpirePatch(clz=CardCrawlGame.class, method="create")
    public static class Create {
        @SpireInstrumentPatch
        public static ExprEditor Instrument() {
            return reportBeforeHandling("CardCrawlGame.create");
        }
    }
}
