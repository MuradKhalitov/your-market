package ru.murad.yourmarket.telegram.handler;

import org.springframework.stereotype.Component;

@Component
public class StartCommandParser {
    public StartAction parse(String text) {
        if (text == null) return StartAction.NOT_START;
        String[] parts = text.trim().split("\\s+", 2);
        if (!"/start".equals(parts[0])) return StartAction.NOT_START;
        return parts.length == 2 && "publish".equals(parts[1]) ? StartAction.PUBLISH : StartAction.MAIN_MENU;
    }

    public enum StartAction {
        NOT_START,
        MAIN_MENU,
        PUBLISH
    }
}
