package com.nexuscraft.nexuscomfort;

/** What players are told when they change a setting, shared by the command and the menu. */
final class Texts {
    private Texts() {
    }

    static String preset(Preset p) {
        return "&aYou are on the &f" + p.title() + "&a preset now. " + p.about();
    }

    static String category(Category c, boolean on) {
        return on
                ? "&a" + c.title() + " is on for you."
                : "&a" + c.title() + " is off for you. It will not happen to you any more.";
    }

    static String backToPreset(Category c, boolean on) {
        return "&a" + c.title() + " follows your preset again (" + (on ? "on" : "off") + ").";
    }
}
