/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.beryx.textio.web;

import org.beryx.textio.TextIO;
import org.beryx.textio.web.SparkDataServer.SessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Session;


import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static spark.Spark.staticFiles;

public class SparkTextIoApp implements TextIoApp<SparkTextIoApp> {
    private static final Logger logger =  LoggerFactory.getLogger(SparkTextIoApp.class);

    private final WebTextTerminal termTemplate;

    private final BiConsumer<TextIO, String> textIoRunner;
    private final SparkDataServer server;
    private Integer maxInactiveSeconds = null;

    private Consumer<String> onDispose;
    private Consumer<String> onAbort;

    public SparkTextIoApp(BiConsumer<TextIO, String> textIoRunner, WebTextTerminal termTemplate) {
        this.textIoRunner = textIoRunner;
        this.termTemplate = termTemplate;
        this.server = new SparkDataServer(this::create, this::get);
    }

    public SparkDataServer getServer() {
        return server;
    }

    @Override
    public void init() {
        server.init();
    }

    public SparkTextIoApp withOnDispose(Consumer<String> onDispose) {
        this.onDispose = onDispose;
        return this;
    }

    public SparkTextIoApp withOnAbort(Consumer<String> onAbort) {
        this.onAbort = onAbort;
        return this;
    }

    public SparkTextIoApp withMaxInactiveSeconds(Integer maxInactiveSeconds) {
        this.maxInactiveSeconds = maxInactiveSeconds;
        return this;
    }

    @Override
    public SparkTextIoApp withStaticFilesLocation(String location) {
        staticFiles.location(location);
        return this;
    }

    @Override
    public SparkTextIoApp withPort(Integer portNumber) {
        if(portNumber != null) {
            server.withPort(portNumber);
        }
        return this;
    }

    @Override
    public int getPort() {
        return server.getPort();
    }

    protected WebTextTerminal create(SessionHolder sessionHolder, String initData) {
        String sessionId = sessionHolder.sessionId;
        Session session = sessionHolder.session;
        logger.debug("Creating terminal for sessionId: {}", sessionId);
        WebTextTerminal terminal = termTemplate.createCopy();
        terminal.setOnDispose(() -> {
            session.removeAttribute(getSessionIdAttribute(sessionId));
            if(onDispose != null) {
                onDispose.accept(sessionId);
            }
        });
        terminal.setOnAbort(() -> {
            session.removeAttribute(getSessionIdAttribute(sessionId));
            if(onAbort != null) {
                onAbort.accept(sessionId);
            }
        });
        session.attribute(getSessionIdAttribute(sessionId), terminal);

        if(maxInactiveSeconds != null) {
            session.maxInactiveInterval(maxInactiveSeconds);
        }

        TextIO textIO = new TextIO(terminal);

        Thread thread = new Thread(() -> textIoRunner.accept(textIO, initData));
        thread.setDaemon(true);
        thread.start();
        return terminal;
    }

    protected WebTextTerminal get(SessionHolder sessionHolder) {
        String sessionId = sessionHolder.sessionId;
        Session session = sessionHolder.session;
        WebTextTerminal terminal = session.attribute(getSessionIdAttribute(sessionId));
        if(terminal == null) {
            throw new DataApiProviderException("Unknown session: " + sessionId);
        }
        logger.trace("Terminal found for sessionId: {} on session with attributes {}", sessionId, session.attributes());
        return terminal;
    }

    private String getSessionIdAttribute(String sessionId) {
        return "web-text-terminal-" + sessionId;
    }
}
