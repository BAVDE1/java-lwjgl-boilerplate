package src;

import src.game.Game;
import src.game.MathUtils;

import static org.lwjgl.glfw.GLFW.*;

public class Main {
    public static void main(String[] args) {
        System.out.printf("Running '%s'%n",  glfwGetVersionString());
        new Game().start();
    }

    /**
     * Proper time stepper.
     * if game has optimised boolean toggled, thread sleeps for half of dt once stepped.
     */
    public static Thread newTimeStepper(double static_dt, Game game) {
        return new Thread() {
            final double halfDt = static_dt * 0.5;  // in seconds

            double accumulator = 0;
            double lastFrame = System.nanoTime();

            public void run() {
                game.createCapabilitiesAndOpen();  // call here so capabilities are current on this thread

                while (!game.shouldClose()) {
                    double t = System.nanoTime();
                    accumulator += MathUtils.nanoToSecond(t - lastFrame);
                    accumulator = Math.min(1, accumulator);  // min of 1 fps (avoid spiral of doom)
                    lastFrame = t;

                    while (accumulator >= static_dt) {
                        accumulator -= static_dt;

                        try {
                            double loopTime = game.mainLoop(static_dt);  // in seconds
                            if (game.optimiseTimeStepper && accumulator + loopTime < halfDt) {  // only sleep if there is enough time
                                Thread.sleep((long) Math.floor(halfDt * 1_000));  // give it a little break *-*
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Program closed while thread was asleep (between frames)");
                        }
                    }
                }
                game.close();
            }
        };
    }
}
