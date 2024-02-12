package me.temper.json.events;

//import org.bukkit.event.Event;
//import org.bukkit.event.HandlerList;
//
///**
// * This class represents a Bukkit event that is triggered when a player saves their data.
// *
// * @author Temper126
// */
//public class DataSaveEvent extends Event {
//
//    private static final HandlerList handlers = new HandlerList();
//
//    /**
//     * The name of the file that was saved.
//     */
//    private String fileName;
//
//    /**
//     * Creates a new DataSaveEvent with the given file name.
//     *
//     * @param fileName the name of the file that was saved
//     */
//    public DataSaveEvent(String fileName) {
//        this.fileName = fileName;
//    }
//
//    /**
//     * Returns the name of the file that was saved.
//     *
//     * @return the name of the file that was saved
//     */
//    public String getFileName() {
//        return fileName;
//    }
//
//    @Override
//    public HandlerList getHandlers() {
//        return handlers;
//    }
//
//    /**
//     * Returns the HandlerList of the registered handlers for this event.
//     *
//     * @return the HandlerList of the registered handlers
//     */
//    public static HandlerList getHandlerList() {
//        return handlers;
//    }
//}
//
