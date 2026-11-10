package com.tangtamukei.game;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * /place structure 로 구조물을 놓을 때 바닐라가 블록마다 찍는 무해한 경고
 * ("Trying to mark a block for post processing ...") 수천 줄을 숨김.
 */
final class LogFilter extends AbstractFilter {

    private static final String NOISE = "mark a block for post processing";

    static void install() {
        try {
            ((Logger) LogManager.getRootLogger()).addFilter(new LogFilter());
        } catch (Throwable ignored) {
            // 로거 구현이 다르면 그냥 둠
        }
    }

    @Override
    public Filter.Result filter(LogEvent event) {
        String msg = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
        return msg != null && msg.contains(NOISE) ? Filter.Result.DENY : Filter.Result.NEUTRAL;
    }
}
