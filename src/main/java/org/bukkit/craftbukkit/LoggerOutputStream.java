// 
// Decompiled by Procyon v0.5.30
// 

package org.bukkit.craftbukkit;

import java.io.IOException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import ru.svarka.Svarka;
import java.io.ByteArrayOutputStream;

public class LoggerOutputStream extends ByteArrayOutputStream
{
    private final String separator;
    private final Level level;
	private Logger logger;
    
    public LoggerOutputStream(final Logger logger, final Level level) {
        this.separator = System.getProperty("line.separator");
        this.logger = Svarka.bukkitLog;
        this.level = level;
    }
    
    @Override
    public void flush() throws IOException {
        synchronized (this) {
            super.flush();
            final String record = this.toString();
            super.reset();
            if (record.length() > 0 && !record.equals(this.separator)) {
                this.logger.log(this.level, record);
            }
        }
    }
}
