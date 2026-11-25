package sim.hw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.firstinspires.ftc.robotcore.external.Func;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import sim.SimClock;

/** Driver Station telemetry: lines are collected and published to the page on update(). */
public final class SimTelemetry implements Telemetry {
    private final Consumer<List<String>> sink;
    private final List<Object> entries = new ArrayList<>();
    private boolean dirty = false;
    private boolean autoClear = true;
    private String itemSeparator = " | ";
    private String captionValueSeparator = " : ";
    private int msInterval = 250;
    private final LogImpl log = new LogImpl();

    public SimTelemetry(Consumer<List<String>> sink) { this.sink = sink; }

    private static String fmt(String format, Object... args) {
        try {
            return String.format(format, args);
        } catch (RuntimeException e) {
            return format + " (bad format: " + e.getMessage() + ")";
        }
    }

    private final class ItemImpl implements Item {
        String caption;
        Object value;
        String format;
        Func<?> producer;
        boolean retained;
        LineImpl line;

        String render() {
            Object v = producer != null ? producer.value() : value;
            String s = format != null && producer != null ? fmt(format, v) : String.valueOf(v);
            return caption + captionValueSeparator + s;
        }

        @Override public String getCaption() { return caption; }
        @Override public Item setCaption(String caption) { this.caption = caption; dirty = true; return this; }
        @Override public Item setValue(String format, Object... args) { value = fmt(format, args); producer = null; dirty = true; return this; }
        @Override public Item setValue(Object value) { this.value = value; producer = null; dirty = true; return this; }
        @Override public <T> Item setValue(Func<T> valueProducer) { producer = valueProducer; format = null; dirty = true; return this; }
        @Override public <T> Item setValue(String format, Func<T> valueProducer) { producer = valueProducer; this.format = format; dirty = true; return this; }
        @Override public Item setRetained(Boolean retained) { this.retained = retained != null && retained; return this; }
        @Override public boolean isRetained() { return retained; }
        @Override public Item addData(String caption, String format, Object... args) { return owner().addData(caption, format, args); }
        @Override public Item addData(String caption, Object value) { return owner().addData(caption, value); }
        @Override public <T> Item addData(String caption, Func<T> valueProducer) { return owner().addData(caption, valueProducer); }
        @Override public <T> Item addData(String caption, String format, Func<T> valueProducer) { return owner().addData(caption, format, valueProducer); }
        private LineImpl owner() {
            if (line == null) {
                line = new LineImpl("");
                int i = entries.indexOf(this);
                line.items.add(this);
                if (i >= 0) entries.set(i, line); else entries.add(line);
            }
            return line;
        }
    }

    private final class LineImpl implements Line {
        final String caption;
        final List<ItemImpl> items = new ArrayList<>();

        LineImpl(String caption) { this.caption = caption; }

        String render() {
            StringBuilder sb = new StringBuilder(caption);
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(itemSeparator);
                sb.append(items.get(i).render());
            }
            return sb.toString();
        }

        private ItemImpl add(ItemImpl it) { it.line = this; items.add(it); dirty = true; return it; }

        @Override public Item addData(String caption, String format, Object... args) {
            ItemImpl it = new ItemImpl(); it.caption = caption; it.value = fmt(format, args); return add(it);
        }
        @Override public Item addData(String caption, Object value) {
            ItemImpl it = new ItemImpl(); it.caption = caption; it.value = value; return add(it);
        }
        @Override public <T> Item addData(String caption, Func<T> valueProducer) {
            ItemImpl it = new ItemImpl(); it.caption = caption; it.producer = valueProducer; return add(it);
        }
        @Override public <T> Item addData(String caption, String format, Func<T> valueProducer) {
            ItemImpl it = new ItemImpl(); it.caption = caption; it.producer = valueProducer; it.format = format; return add(it);
        }
    }

    private final class LogImpl implements Log {
        final List<String> lines = new ArrayList<>();
        int capacity = 9;
        DisplayOrder order = DisplayOrder.OLDEST_FIRST;

        @Override public int getCapacity() { return capacity; }
        @Override public void setCapacity(int capacity) { this.capacity = capacity; }
        @Override public DisplayOrder getDisplayOrder() { return order; }
        @Override public void setDisplayOrder(DisplayOrder displayOrder) { order = displayOrder; }
        @Override public void add(String entry) {
            lines.add(entry);
            while (lines.size() > capacity) lines.remove(0);
            dirty = true;
        }
        @Override public void add(String format, Object... args) { add(fmt(format, args)); }
        @Override public void clear() { lines.clear(); dirty = true; }
    }

    private ItemImpl addItem(ItemImpl it) { entries.add(it); dirty = true; return it; }

    @Override public synchronized Item addData(String caption, String format, Object... args) {
        ItemImpl it = new ItemImpl(); it.caption = caption; it.value = fmt(format, args); return addItem(it);
    }
    @Override public synchronized Item addData(String caption, Object value) {
        ItemImpl it = new ItemImpl(); it.caption = caption; it.value = value; return addItem(it);
    }
    @Override public synchronized <T> Item addData(String caption, Func<T> valueProducer) {
        ItemImpl it = new ItemImpl(); it.caption = caption; it.producer = valueProducer; return addItem(it);
    }
    @Override public synchronized <T> Item addData(String caption, String format, Func<T> valueProducer) {
        ItemImpl it = new ItemImpl(); it.caption = caption; it.producer = valueProducer; it.format = format; return addItem(it);
    }
    @Override public synchronized boolean removeItem(Item item) { dirty = true; return entries.remove(item); }

    @Override public synchronized void clear() {
        entries.removeIf(e -> !(e instanceof ItemImpl) || !((ItemImpl) e).retained);
    }
    @Override public synchronized void clearAll() { entries.clear(); log.lines.clear(); dirty = true; }
    @Override public Object addAction(Runnable action) { return action; }
    @Override public boolean removeAction(Object token) { return true; }
    @Override public void speak(String text) {}
    @Override public void speak(String text, String languageCode, String countryCode) {}

    @Override public synchronized boolean update() {
        SimClock.touch();
        if (!dirty) return false;
        List<String> out = new ArrayList<>();
        for (Object e : entries) {
            out.add(e instanceof LineImpl ? ((LineImpl) e).render() : ((ItemImpl) e).render());
        }
        out.addAll(log.lines);
        sink.accept(out);
        if (autoClear) clear();
        dirty = false;
        return true;
    }

    @Override public synchronized Line addLine() { return addLine(""); }
    @Override public synchronized Line addLine(String lineCaption) {
        LineImpl l = new LineImpl(lineCaption);
        entries.add(l);
        dirty = true;
        return l;
    }
    @Override public synchronized boolean removeLine(Line line) { dirty = true; return entries.remove(line); }
    @Override public boolean isAutoClear() { return autoClear; }
    @Override public void setAutoClear(boolean autoClear) { this.autoClear = autoClear; }
    @Override public int getMsTransmissionInterval() { return msInterval; }
    @Override public void setMsTransmissionInterval(int msTransmissionInterval) { msInterval = msTransmissionInterval; }
    @Override public String getItemSeparator() { return itemSeparator; }
    @Override public void setItemSeparator(String itemSeparator) { this.itemSeparator = itemSeparator; }
    @Override public String getCaptionValueSeparator() { return captionValueSeparator; }
    @Override public void setCaptionValueSeparator(String captionValueSeparator) { this.captionValueSeparator = captionValueSeparator; }
    @Override public void setDisplayFormat(DisplayFormat displayFormat) {}
    @Override public Log log() { return log; }
}
