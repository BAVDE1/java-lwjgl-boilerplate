package src.rendering.text;

import src.game.Constants;
import src.rendering.*;
import src.utility.Logging;
import src.utility.Vec2;

import java.util.ArrayList;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;

public class TextRenderer {
    public static class TextObject {
        private TextRenderer parent;
        private float scale = 1;
        private int ySpacing = 5;

        private int loadedFontId;
        private String string;
        private Vec2 pos;

        private final StripBuilder2f sb = new StripBuilder2f(true);
        private boolean hasChanged = true;

        public TextObject(int loadedFontId, String string, Vec2 pos, float scale, int ySpacing) {
            this(loadedFontId, string, pos);
            this.scale = scale;
            this.ySpacing = ySpacing;
        }

        public TextObject(int loadedFontId, String string, Vec2 pos) {
            this.loadedFontId = loadedFontId;
            this.string = string;
            this.pos = pos;
        }

        private void addParent(TextRenderer parent) {
            assert this.parent == null;
            this.parent = parent;
        }

        private void removeParent() {
            assert this.parent != null;
            this.parent = null;
        }

        private float[] buildStrip() {
            if (!hasChanged) return sb.getSetVertices();  // don't even bother re-building

            sb.clear();
            sb.setAdditionalVerts(VertexArray.Layout.defaultLayoutAdditionalVerts());

            FontManager.LoadedFont font = FontManager.getLoadedFont(loadedFontId);
            int genericHeight = (int) (font.glyphMap.get(' ').height * scale);

            int accumulatedY = 0;
            for (String line : string.split("\n")) {
                if (line.isEmpty()) {
                    accumulatedY += genericHeight + ySpacing;
                    continue;
                }

                // all chars in line
                float lineY = pos.y + accumulatedY;
                int accumulatedX = 0;
                for (char c : line.toCharArray()) {
                    if (!font.glyphMap.containsKey(c)) {
                        Logging.warn("Character '%s' does not exist in the currently loaded font. Using '0' instead.", c);
                        c = '0';
                    }

                    FontManager.Glyph glyph = font.glyphMap.get(c);
                    Vec2 size = new Vec2(glyph.width, glyph.height).mul(scale);
                    Vec2 topLeft = new Vec2(pos.x + accumulatedX, lineY);

                    Shape.Mode mode = new Shape.Mode(FontManager.FONT_TEXTURE_SLOT, glyph.texTopLeft, glyph.texSize);
                    Shape.Quad quad = Shape.createRect(topLeft, size, mode);
                    if (accumulatedX == 0) sb.pushSeparatedQuad(quad);
                    else sb.pushQuad(quad);

                    accumulatedX += (int) size.x;
                }
                accumulatedY += genericHeight + ySpacing;
            }

            hasChanged = false;
            return sb.getSetVertices();
        }

        public String getString() {return string;}
        public void setString(String newString, Object... args) {
            setString(String.format(newString, args));
        }

        public void setString(String newString) {
            if (!Objects.equals(newString, string)) {
                string = newString;
                hasChanged = true;
                if (parent != null) parent.hasBeenModified = true;
            }
        }

        public Vec2 getPos() {return pos.getClone();}
        public void setPos(Vec2 newPos) {
            if (newPos != pos) {
                pos = newPos;
                hasChanged = true;
                if (parent != null) parent.hasBeenModified = true;
            }
        }

        public int getLoadedFontId() {return loadedFontId;}
        public void setFontId(int newFontId) {
            if (newFontId != loadedFontId) {
                loadedFontId = newFontId;
                hasChanged = true;
                if (parent != null) parent.hasBeenModified = true;
            }
        }

        public float getScale() {return scale;}
        public void setScale(float newScale) {
            if (newScale != scale) {
                scale = newScale;
                hasChanged = true;
                if (parent != null) parent.hasBeenModified = true;
            }
        }

        public int getYSpacing() {return ySpacing;}
        public void setYSpacing(int newYSpacing) {
            if (newYSpacing != ySpacing) {
                ySpacing = newYSpacing;
                hasChanged = true;
                if (parent != null) parent.hasBeenModified = true;
            }
        }
    }

    private final ArrayList<TextObject> textObjects = new ArrayList<>();

    private VertexArray va;
    private VertexBuffer vb;
    private StripBuilder2f sb;

    private boolean hasBeenModified = false;

    /** after GL context created */
    public void setupBufferObjects() {
        va = new VertexArray();   va.genId();
        vb = new VertexBuffer();  vb.genId();
        sb = new StripBuilder2f(true);

        sb.setAdditionalVerts(VertexArray.Layout.defaultLayoutAdditionalVerts());
        va.addBuffer(vb, VertexArray.Layout.getDefaultLayout());
    }

    private void buildBuffer() {
        sb.clear();

        for (TextObject to : textObjects) {
            if (to.string.isEmpty() || to.scale < Constants.EPSILON) continue;
            sb.pushRawSeparatedVertices(to.buildStrip());
        }

        Renderer.bindBuffer(vb);
        vb.bufferData(sb.getSetVertices());
        hasBeenModified = false;
    }

    public void draw() {
        if (hasBeenModified) buildBuffer();

        if (sb.getFloatCount() > 0) {
            Renderer.draw(GL_TRIANGLE_STRIP, va, sb.getVertexCount());
        }
    }

    public ArrayList<TextObject> getTextObjects() {
        return textObjects;
    }

    public void pushTextObject(TextObject to) {
        to.addParent(this);
        textObjects.add(to);
        hasBeenModified = true;
    }

    public void removeTextObject(TextObject to) {
        to.removeParent();
        textObjects.remove(to);
        hasBeenModified = true;
    }

    public void clearAllTextObjects() {
        for (TextObject to : textObjects) to.removeParent();
        textObjects.clear();
        hasBeenModified = true;
    }
}
