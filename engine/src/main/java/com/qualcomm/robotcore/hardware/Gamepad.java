package com.qualcomm.robotcore.hardware;

import java.util.Set;

/**
 * Stand-in for the SDK Gamepad: same public fields, same edge-detection methods
 * (bWasPressed() and friends return true once per press), same 0.2 stick deadzone.
 */
public class Gamepad {
    public enum Type { UNKNOWN, LOGITECH_F310, XBOX_360, SONY_PS4, SONY_PS4_SUPPORTED_BY_KERNEL }

    public static final int ID_UNASSOCIATED = -1;
    public static final int ID_SYNTHETIC = -2;

    public volatile Type type = Type.XBOX_360;
    public volatile float left_stick_x = 0f;
    public volatile float left_stick_y = 0f;
    public volatile float right_stick_x = 0f;
    public volatile float right_stick_y = 0f;
    public volatile boolean dpad_up = false;
    public volatile boolean dpad_down = false;
    public volatile boolean dpad_left = false;
    public volatile boolean dpad_right = false;
    public volatile boolean a = false;
    public volatile boolean b = false;
    public volatile boolean x = false;
    public volatile boolean y = false;
    public volatile boolean guide = false;
    public volatile boolean start = false;
    public volatile boolean back = false;
    public volatile boolean left_bumper = false;
    public volatile boolean right_bumper = false;
    public volatile boolean left_stick_button = false;
    public volatile boolean right_stick_button = false;
    public volatile float left_trigger = 0f;
    public volatile float right_trigger = 0f;
    public volatile boolean left_trigger_pressed = false;
    public volatile boolean right_trigger_pressed = false;
    public volatile boolean circle = false;
    public volatile boolean cross = false;
    public volatile boolean triangle = false;
    public volatile boolean square = false;
    public volatile boolean share = false;
    public volatile boolean options = false;
    public volatile boolean touchpad = false;
    public volatile boolean touchpad_finger_1 = false;
    public volatile boolean touchpad_finger_2 = false;
    public volatile float touchpad_finger_1_x = 0f;
    public volatile float touchpad_finger_1_y = 0f;
    public volatile float touchpad_finger_2_x = 0f;
    public volatile float touchpad_finger_2_y = 0f;
    public volatile boolean ps = false;
    public volatile int id = ID_UNASSOCIATED;
    public volatile long timestamp = 0;

    protected float joystickDeadzone = 0.2f;
    private float triggerThreshold = 0.2f;

    private static final String[] NAMES = {
        "dpad_up", "dpad_down", "dpad_left", "dpad_right", "a", "b", "x", "y", "guide", "start", "back",
        "left_bumper", "right_bumper", "left_stick_button", "right_stick_button", "touchpad",
        "left_trigger", "right_trigger"
    };
    private final boolean[] state = new boolean[NAMES.length];
    private final boolean[] pressed = new boolean[NAMES.length];
    private final boolean[] released = new boolean[NAMES.length];

    public Gamepad() {}

    /** Simulator side: push raw controller values in (sticks -1..1, y negative when pushed away). */
    public synchronized void simUpdate(float lx, float ly, float rx, float ry, float lt, float rt, Set<String> down) {
        left_stick_x = clean(lx);
        left_stick_y = clean(ly);
        right_stick_x = clean(rx);
        right_stick_y = clean(ry);
        left_trigger = lt;
        right_trigger = rt;
        left_trigger_pressed = lt > triggerThreshold;
        right_trigger_pressed = rt > triggerThreshold;
        for (int i = 0; i < NAMES.length; i++) {
            boolean now;
            if (i == 16) now = left_trigger_pressed;
            else if (i == 17) now = right_trigger_pressed;
            else now = down.contains(NAMES[i]);
            if (now && !state[i]) pressed[i] = true;
            if (!now && state[i]) released[i] = true;
            state[i] = now;
        }
        dpad_up = state[0]; dpad_down = state[1]; dpad_left = state[2]; dpad_right = state[3];
        a = state[4]; b = state[5]; x = state[6]; y = state[7];
        guide = state[8]; start = state[9]; back = state[10];
        left_bumper = state[11]; right_bumper = state[12];
        left_stick_button = state[13]; right_stick_button = state[14];
        touchpad = state[15];
        cross = a; circle = b; square = x; triangle = y; share = back; options = start; ps = guide;
        timestamp = System.currentTimeMillis();
    }

    private float clean(float v) {
        if (Math.abs(v) < joystickDeadzone) return 0f;
        if (v > 1f) v = 1f;
        if (v < -1f) v = -1f;
        float sign = v > 0 ? 1f : -1f;
        return sign * (Math.abs(v) - joystickDeadzone) / (1f - joystickDeadzone);
    }

    private synchronized boolean takePressed(int i) { boolean r = pressed[i]; pressed[i] = false; return r; }
    private synchronized boolean takeReleased(int i) { boolean r = released[i]; released[i] = false; return r; }

    public synchronized void resetEdgeDetection() {
        java.util.Arrays.fill(pressed, false);
        java.util.Arrays.fill(released, false);
    }

    public void setJoystickDeadzone(float deadzone) { this.joystickDeadzone = deadzone; }
    public void setTriggerThreshold(float threshold) { this.triggerThreshold = threshold; }
    public float getTriggerThreshold() { return triggerThreshold; }
    public void setGamepadId(int id) { this.id = id; }
    public int getGamepadId() { return id; }
    public Type type() { return type; }

    public boolean atRest() {
        return left_stick_x == 0f && left_stick_y == 0f && right_stick_x == 0f && right_stick_y == 0f
                && left_trigger == 0f && right_trigger == 0f;
    }

    public void rumble(int durationMs) {}
    public void rumble(double rumble1, double rumble2, int durationMs) {}
    public void rumbleBlips(int count) {}
    public void stopRumble() {}
    public boolean isRumbling() { return false; }
    public void setLedColor(double r, double g, double b, int durationMs) {}

    public boolean dpadUpWasPressed() { return takePressed(0); }
    public boolean dpadUpWasReleased() { return takeReleased(0); }
    public boolean dpadDownWasPressed() { return takePressed(1); }
    public boolean dpadDownWasReleased() { return takeReleased(1); }
    public boolean dpadLeftWasPressed() { return takePressed(2); }
    public boolean dpadLeftWasReleased() { return takeReleased(2); }
    public boolean dpadRightWasPressed() { return takePressed(3); }
    public boolean dpadRightWasReleased() { return takeReleased(3); }
    public boolean aWasPressed() { return takePressed(4); }
    public boolean aWasReleased() { return takeReleased(4); }
    public boolean bWasPressed() { return takePressed(5); }
    public boolean bWasReleased() { return takeReleased(5); }
    public boolean xWasPressed() { return takePressed(6); }
    public boolean xWasReleased() { return takeReleased(6); }
    public boolean yWasPressed() { return takePressed(7); }
    public boolean yWasReleased() { return takeReleased(7); }
    public boolean guideWasPressed() { return takePressed(8); }
    public boolean guideWasReleased() { return takeReleased(8); }
    public boolean startWasPressed() { return takePressed(9); }
    public boolean startWasReleased() { return takeReleased(9); }
    public boolean backWasPressed() { return takePressed(10); }
    public boolean backWasReleased() { return takeReleased(10); }
    public boolean leftBumperWasPressed() { return takePressed(11); }
    public boolean leftBumperWasReleased() { return takeReleased(11); }
    public boolean rightBumperWasPressed() { return takePressed(12); }
    public boolean rightBumperWasReleased() { return takeReleased(12); }
    public boolean leftStickButtonWasPressed() { return takePressed(13); }
    public boolean leftStickButtonWasReleased() { return takeReleased(13); }
    public boolean rightStickButtonWasPressed() { return takePressed(14); }
    public boolean rightStickButtonWasReleased() { return takeReleased(14); }
    public boolean touchpadWasPressed() { return takePressed(15); }
    public boolean touchpadWasReleased() { return takeReleased(15); }
    public boolean leftTriggerWasPressed() { return takePressed(16); }
    public boolean leftTriggerWasReleased() { return takeReleased(16); }
    public boolean rightTriggerWasPressed() { return takePressed(17); }
    public boolean rightTriggerWasReleased() { return takeReleased(17); }
    public boolean crossWasPressed() { return takePressed(4); }
    public boolean crossWasReleased() { return takeReleased(4); }
    public boolean circleWasPressed() { return takePressed(5); }
    public boolean circleWasReleased() { return takeReleased(5); }
    public boolean squareWasPressed() { return takePressed(6); }
    public boolean squareWasReleased() { return takeReleased(6); }
    public boolean triangleWasPressed() { return takePressed(7); }
    public boolean triangleWasReleased() { return takeReleased(7); }
    public boolean psWasPressed() { return takePressed(8); }
    public boolean psWasReleased() { return takeReleased(8); }
    public boolean optionsWasPressed() { return takePressed(9); }
    public boolean optionsWasReleased() { return takeReleased(9); }
    public boolean shareWasPressed() { return takePressed(10); }
    public boolean shareWasReleased() { return takeReleased(10); }

    @Override public String toString() {
        return String.format("ID: %2d lx: % 1.2f ly: % 1.2f rx: % 1.2f ry: % 1.2f lt: %1.2f rt: %1.2f",
                id, left_stick_x, left_stick_y, right_stick_x, right_stick_y, left_trigger, right_trigger);
    }
}
