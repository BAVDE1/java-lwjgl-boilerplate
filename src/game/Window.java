package src.game;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    public long handle;

    public Window() {
        GLFWErrorCallback.createPrint(System.err).set();  // print errors please *-*

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        // Configure window
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        // Create the window
        handle = glfwCreateWindow(400, 400, "ARGG IT HURTS", NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Failed to create the GLFW window");
    }

    public void show() {
        // center the window (if it can)
        if (glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {  // cause wayland is stupid
            try (MemoryStack stack = stackPush()) {  // Get the thread stack and push a new frame
                IntBuffer winWidth = stack.mallocInt(1);
                IntBuffer winHeight = stack.mallocInt(1);

                glfwGetWindowSize(handle, winWidth, winHeight);
                GLFWVidMode screen = glfwGetVideoMode(glfwGetPrimaryMonitor());
                assert screen != null;

                glfwSetWindowPos(
                        handle,
                        (screen.width() - winWidth.get(0)) / 2,
                        (screen.height() - winHeight.get(0)) / 2
                );
            }
        }

        // make context current & show
        glfwMakeContextCurrent(handle);
        glfwShowWindow(handle);
    }

    public void setVSync(boolean vSync) {
        glfwSwapInterval(vSync ? 1 : 0);
    }
}