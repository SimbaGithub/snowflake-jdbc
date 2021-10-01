/*
 * Copyright (c) 2012-2021 Snowflake Computing Inc. All rights reserved.
 */
package net.snowflake.client.log;

import static net.snowflake.client.log.SFFormatter.SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE;

import java.util.logging.*;
import net.snowflake.client.category.TestCategoryCore;
import org.junit.*;
import org.junit.experimental.categories.Category;

/** A class for testing {@link JDK14Logger} */
@Category(TestCategoryCore.class)
public class JDK14LoggerLatestIT extends AbstractLoggerIT {
  /** {@link JDK14Logger} instance that will be tested in this class */
  private static final JDK14Logger LOGGER = new JDK14Logger(JDK14LoggerLatestIT.class.getName());

  /**
   * Used for storing the log level set on the logger instance before starting the tests. Once the
   * tests are run, this log level will be restored.
   */
  private static Level logLevelToRestore;

  /**
   * This is the logger instance internally used by the JDK14Logger instance. Logger.getLogger()
   * returns the cached instance if an instance by the same name was created before. The name used
   * for creating JDK14Logger instance is also used here to get the same internal logger instance.
   *
   * <p>JDK14Logger doesn't expose methods to add handlers and set other configurations, hence
   * direct access to the internal logger is required.
   */
  private static final Logger internalLogger =
      Logger.getLogger(JDK14LoggerLatestIT.class.getName());

  /**
   * Used for storing whether the parent logger handlers were run in the logger instance before
   * starting the tests. Once the tests are run, the behavior will be restored.
   */
  private static boolean useParentHandlersToRestore = true;

  /** Used for storing SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE before starting the tests. */
  private static String dumpStackToRestore = null;

  /** This handler will be added to the internal logger to get logged messages. */
  private TestJDK14LogHandler handler = new TestJDK14LogHandler(new SFFormatter());

  /** Message last logged using JDK14Logger. */
  private String lastLogMessage = null;

  /** Output string logged by Formatter.format function */
  private String lastLogOutput = null;

  /** Level at which last message was logged using JDK14Logger. */
  private Level lastLogMessageLevel = null;

  @BeforeClass
  public static void oneTimeSetUp() {
    logLevelToRestore = internalLogger.getLevel();
    useParentHandlersToRestore = internalLogger.getUseParentHandlers();

    internalLogger.setUseParentHandlers(false);

    dumpStackToRestore = System.getProperty(SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE);
    System.clearProperty(SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE);
  }

  @AfterClass
  public static void oneTimeTearDown() {
    internalLogger.setLevel(logLevelToRestore);
    internalLogger.setUseParentHandlers(useParentHandlersToRestore);
    if (dumpStackToRestore != null) {
      System.setProperty(SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE, dumpStackToRestore);
    }
  }

  @Before
  public void setUp() {
    super.setUp();
    internalLogger.addHandler(this.handler);
  }

  @After
  public void tearDown() {
    internalLogger.removeHandler(this.handler);
  }

  @Override
  void logMessage(LogLevel level, String message, Object... args) {
    switch (level) {
      case ERROR:
        LOGGER.error(message, args);
        break;
      case WARNING:
        LOGGER.warn(message, args);
        break;
      case INFO:
        LOGGER.info(message, args);
        break;
      case DEBUG:
        LOGGER.debug(message, args);
        break;
      case TRACE:
        LOGGER.trace(message, args);
        break;
    }
  }

  @Override
  void setLogLevel(LogLevel level) {
    internalLogger.setLevel(toJavaCoreLoggerLevel(level));
  }

  @Override
  String getLoggedMessage() {
    return this.lastLogMessage;
  }

  String getLoggedOutput() {
    return this.lastLogOutput;
  }

  @Override
  LogLevel getLoggedMessageLevel() {
    return fromJavaCoreLoggerLevel(this.lastLogMessageLevel);
  }

  @Override
  void clearLastLoggedMessageAndLevel() {
    this.lastLogMessage = null;
    this.lastLogOutput = null;
    this.lastLogMessageLevel = null;
  }

  /** Converts log levels in {@link LogLevel} to appropriate levels in {@link Level}. */
  private Level toJavaCoreLoggerLevel(LogLevel level) {
    switch (level) {
      case ERROR:
        return Level.SEVERE;
      case WARNING:
        return Level.WARNING;
      case INFO:
        return Level.INFO;
      case DEBUG:
        return Level.FINE;
      case TRACE:
        return Level.FINEST;
    }

    return Level.FINEST;
  }

  /** Converts log levels in {@link Level} to appropriate levels in {@link LogLevel}. */
  private LogLevel fromJavaCoreLoggerLevel(Level level) {
    if (Level.SEVERE.equals(level)) {
      return LogLevel.ERROR;
    }
    if (Level.WARNING.equals(level)) {
      return LogLevel.WARNING;
    }
    if (Level.INFO.equals(level)) {
      return LogLevel.INFO;
    }
    if (Level.FINE.equals(level) || Level.FINER.equals(level)) {
      return LogLevel.DEBUG;
    }
    if (Level.FINEST.equals(level) || Level.ALL.equals(level)) {
      return LogLevel.TRACE;
    }

    throw new IllegalArgumentException(
        String.format("Specified log level '%s' not supported", level.toString()));
  }

  /** An handler that will be used for getting messages logged by a {@link Logger} instance */
  private class TestJDK14LogHandler extends Handler {
    /**
     * Creates an instance of {@link TestJDK14LogHandler}
     *
     * @param formatter Formatter that will be used for formatting log records in this handler
     *     instance
     */
    TestJDK14LogHandler(Formatter formatter) {
      super();
      super.setFormatter(formatter);
    }

    @Override
    public void publish(LogRecord record) {
      // Assign the log message, log output and it's level to the outer class instance
      // variables so that it can see the messages logged
      lastLogMessage = getFormatter().formatMessage(record);
      lastLogOutput = getFormatter().format(record);
      lastLogMessageLevel = record.getLevel();
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  /**
   * This test intends to check if the exception stack trace will be generated in JDK14Logger with
   * SFFormatter.
   */
  @Test
  public void testLogException() {
    final String exceptionStr = "FakeExceptionInStack";
    clearLastLoggedMessageAndLevel();

    LOGGER.debug("test exception, no stack", new Exception(exceptionStr));
    String loggedMsg = getLoggedOutput();
    Assert.assertFalse(
        "Log output should not contain stack trace for exception",
        loggedMsg.contains(exceptionStr));

    try {
      System.setProperty(SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE, "true");
      LOGGER.debug("test exception, dump stack", new Exception(exceptionStr));

      loggedMsg = getLoggedOutput();
      Assert.assertTrue(
          "Log output should contain stack trace for exception", loggedMsg.contains(exceptionStr));
    } finally {
      System.clearProperty(SYS_PROPERTY_SF_FORMATTER_DUMP_STACKTRACE);
    }
  }
}
