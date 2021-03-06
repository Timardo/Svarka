// 
// Decompiled by Procyon v0.5.30
// 

package org.bukkit.craftbukkit;

import net.minecraft.server.management.UserList;
import java.io.IOException;
import org.bukkit.Bukkit;
import net.minecraft.server.management.UserListIPBansEntry;
import ru.svarka.Svarka;
import java.util.Date;
import net.minecraft.server.management.UserListIPBans;

import org.apache.logging.log4j.Level;
import org.bukkit.BanEntry;

public final class CraftIpBanEntry implements BanEntry
{
    private final UserListIPBans list;
    private final String target;
    private Date created;
    private String source;
    private Date expiration;
    private String reason;
    
    public CraftIpBanEntry(final String target, final UserListIPBansEntry entry, final UserListIPBans list) {
        this.list = list;
        this.target = target;
        this.created = ((entry.getCreated() != null) ? new Date(entry.getCreated().getTime()) : null);
        this.source = entry.getSource();
        this.expiration = ((entry.getBanEndDate() != null) ? new Date(entry.getBanEndDate().getTime()) : null);
        this.reason = entry.getBanReason();
    }
    
    @Override
    public String getTarget() {
        return this.target;
    }
    
    @Override
    public Date getCreated() {
        return (this.created == null) ? null : ((Date)this.created.clone());
    }
    
    @Override
    public void setCreated(final Date created) {
        this.created = created;
    }
    
    @Override
    public String getSource() {
        return this.source;
    }
    
    @Override
    public void setSource(final String source) {
        this.source = source;
    }
    
    @Override
    public Date getExpiration() {
        return (this.expiration == null) ? null : ((Date)this.expiration.clone());
    }
    
    @Override
    public void setExpiration(Date expiration) {
        if (expiration != null && expiration.getTime() == new Date(0, 0, 0, 0, 0, 0).getTime()) {
            expiration = null;
        }
        this.expiration = expiration;
    }
    
    @Override
    public String getReason() {
        return this.reason;
    }
    
    @Override
    public void setReason(final String reason) {
        this.reason = reason;
    }
    
    @Override
    public void save() {
        final UserListIPBansEntry entry = new UserListIPBansEntry(this.target, this.created, this.source, this.expiration, this.reason);
        (/*(UserList<K, UserListIPBansEntry>)*/this.list).addEntry(entry);
        try {
            this.list.writeChanges();
        }
        catch (IOException ex) {
        	Svarka.bukkitLog.log(Level.ERROR, "Failed to save banned-ips.json, {0}", ex.getMessage());
        }
    }
}
