package src.rendering;

import src.game.Constants;
import src.utility.Logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;

import static org.lwjgl.opengl.GL45.*;

/**
 * Loads, compiles and links shaders from file / file paths to its own shader program.
 */
public class ShaderHelper {
    public static class Shader {
        int id, type;
        String absFilePath, fileName;

        Shader(int id, int type, File file) {
            this.id = id;
            this.type = type;
            absFilePath = file.getAbsolutePath();
            fileName = file.getName();
        }

        @Override
        public String toString() {
            return String.format("Shader(%s, %s, %s)", id, getShaderTypeString(type), fileName);
        }
    }

    public final HashMap<String, Integer> uniformCache = new HashMap<>();
    public final ArrayList<Shader> attachedShaders = new ArrayList<>();

    private Integer program;
    private boolean linked = false;

    /** Gen program, attach shader multi, & then link program */
    public void autoInitializeShadersMulti(String filePath) {
        genProgram();
        attachShaderMulti(filePath);
        linkProgram();
    }

    public void genProgram() {
        if (program != null) {
            Logging.warn("Attempting to re-generate already generated program, aborting");
            return;
        }
        program = glCreateProgram();
    }

    /** Search entire directory (including folders within folders) until a file is found, and pass it to applyShader() */
    public void attachShadersInDir(File dir) {
        for (final File fileEntry : Objects.requireNonNull(dir.listFiles())) {
            if (fileEntry.isDirectory()) {
                if (fileEntry.getName().equals("ignore")) continue;
                attachShadersInDir(fileEntry.getAbsoluteFile());
                continue;
            }
            attachShader(fileEntry);
        }
    }

    /** Attach the 2 necessary shaders */
    public void attachShaders(String... allFilePaths) {
        for (String filePath : allFilePaths) {
            attachShader(new File(filePath));
        }
    }

    /** Attach different shaders that are in the same file. MUST be separated with a "//--- SHADER_TYPE" line */
    public void attachShaderMulti(String filePath) {
        int currentShaderType = -1;

        File file = new File(filePath);
        try {
            Scanner scanner = new Scanner(file);
            StringBuilder charSequence = new StringBuilder();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("//---")) {
                    // finish last shader
                    if (!charSequence.isEmpty()) {
                        attachShader(charSequence.toString(), currentShaderType, file);
                    }

                    // start next shader
                    line = line.replace(" ", "").toLowerCase();
                    currentShaderType = getShaderType(line.split("//---")[1]);
                    charSequence = new StringBuilder();
                    continue;
                }

                charSequence.append("\n").append(line);
            }

            // finish
            attachShader(charSequence.toString(), currentShaderType, file);
        } catch (FileNotFoundException e) {
            Logging.danger("'%s' at '%s' could not be read.\nError message: %s%n", file.getName(), file.getAbsolutePath(), e);
        }
    }

    /**
     * adds new GL shader from given file (if applicable)
     * <a href="https://docs.gl/gl2/glAttachShader">Shader setup example</a>
     */
    public void attachShader(File file) {
        int shaderType = getShaderType(file);
        if (shaderType < 0) return;  // not a shader file

        // get file contents
        String charSequence;
        try {
            Scanner scanner = new Scanner(file);
            StringBuilder fileContents = new StringBuilder();
            while (scanner.hasNextLine()) {
                fileContents.append("\n").append(scanner.nextLine());
            }
            charSequence = fileContents.toString();
        } catch (FileNotFoundException e) {
            Logging.danger("'%s' at '%s' could not be read.\nError message: %s%n", file.getName(), file.getAbsolutePath(), e);
            return;
        }

        attachShader(charSequence, shaderType, file);
    }

    public void attachShader(String shaderCode, int shaderType, File sourceFile) {
        if (program == null || program < 0) {
            Logging.danger("No program set or program failed to be created.");
            return;
        }

        // compile shader
        int shader = glCreateShader(shaderType);
        glShaderSource(shader, shaderCode);

        glCompileShader(shader);
        int[] shaderCompiled = new int[1];  // only needs size of 1
        glGetShaderiv(shader, GL_COMPILE_STATUS, shaderCompiled);
        if (shaderCompiled[0] != GL_TRUE) {
            Logging.danger("Shader Compile Error (%s): %s", sourceFile, glGetShaderInfoLog(shader, 1024));
            return;
        }

        glAttachShader(program, shader);
        attachedShaders.add(new Shader(shader, shaderType, sourceFile));

    }

    public void linkProgram() {
        if (linked) return;

        linked = true;
        glLinkProgram(program);
        int[] programLinked = new int[1];

        glGetProgramiv(program, GL_LINK_STATUS, programLinked);
        if (programLinked[0] != GL_TRUE) {
            Logging.danger("Shader Linking Error: %s", glGetProgramInfoLog(program, 1024));
            return;
        }
        Logging.debug("Shaders %s [linked with program %s]", attachedShaders, program);
    }

    /** <a href="https://www.khronos.org/opengl/wiki/Shader">OpenGL shaders</a> */
    private static int getShaderType(File file) {
        String[] splitName = file.getName().split("\\.");
        return getShaderType(splitName[splitName.length - 1]);  // file extension
    }

    private static int getShaderType(String typeString) {
        return switch (typeString) {
            case "vert" -> GL_VERTEX_SHADER;
            case "tesc" -> GL_TESS_CONTROL_SHADER;
            case "tese" -> GL_TESS_EVALUATION_SHADER;
            case "geom" -> GL_GEOMETRY_SHADER;
            case "frag" -> GL_FRAGMENT_SHADER;
            default -> -1;
        };
    }

    private static String getShaderTypeString(int shaderType) {
        return switch (shaderType) {
            case GL_VERTEX_SHADER -> "VERTEX";
            case GL_TESS_CONTROL_SHADER -> "TESS_CONTROL";
            case GL_TESS_EVALUATION_SHADER -> "TESS_EVALUATION";
            case GL_GEOMETRY_SHADER -> "GEOMETRY";
            case GL_FRAGMENT_SHADER -> "FRAGMENT";
            default -> "UNKNOWN";
        };
    }

    public static void uniformResolutionData(ShaderHelper sh) {
        ShaderHelper.uniform2f(sh, "resolution", Constants.SCREEN_SIZE.width, Constants.SCREEN_SIZE.height);
        ShaderHelper.uniformMatrix4f(sh, "projectionMatrix", Constants.PROJECTION_MATRIX);
    }

    public void bind() {Renderer.bindShader(this);}
    public void unbind() {Renderer.unBindShader();}
    public int getProgram() {return program;}

    public int getUniformLocation(String uniform) {
        if (!linked) {
            Logging.danger("Shader has not been linked!");
            return -1;
        }

        bind();
        if (!uniformCache.containsKey(uniform)) {
            uniformCache.put(uniform, glGetUniformLocation(program, uniform));
        }
        return uniformCache.get(uniform);
    }

    public static void uniform1i(ShaderHelper sh, String uniform, int i) {
        glUniform1i(sh.getUniformLocation(uniform), i);
    }
    public static void uniform1iv(ShaderHelper sh, String uniform, int[] intArray) {
        glUniform1iv(sh.getUniformLocation(uniform), intArray);
    }
    public static void uniform1f(ShaderHelper sh, String uniform, float f) {
        glUniform1f(sh.getUniformLocation(uniform), f);
    }
    public static void uniform2f(ShaderHelper sh, String uniform, float f1, float f2) {
        glUniform2f(sh.getUniformLocation(uniform), f1, f2);
    }
    public static void uniformMatrix4f(ShaderHelper sh, String uniform, float[] matrix4f) {
        if (matrix4f.length != 4*4) {
            Logging.danger("matrix4 given does not have exactly %s items", 4*4);
            return;
        }
        glUniformMatrix4fv(sh.getUniformLocation(uniform), false, matrix4f);
    }
}
