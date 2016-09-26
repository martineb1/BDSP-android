package me.sunyfusion.bdsp.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/**
 * Created by Robert Wieland on 3/26/16.
 */

public class BdspDB extends SQLiteOpenHelper
{
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Tasks.db";
    public static final String TABLE_NAME = "tasksTable";
    public static SQLiteDatabase db;

    public ArrayList<String> deleteQueue;
    Context c;

    /**
     * Creates new BdspDB object
     * @param context the context of the Activity that is using this object
     */
    public BdspDB(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        db = this.getWritableDatabase();
        deleteQueue = new ArrayList<String>();
        c = context;
    }

    /**
     * Inserts values into database
     * @param values values to be inserted
     */
    public void insert(ContentValues values) {
        db.insert(TABLE_NAME,null,values);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL("create table " + TABLE_NAME + " (unique_table_id INTEGER PRIMARY KEY)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {

    }

    /**
     * Adds a column to the database
     * @param columnName name of the column
     * @param columnType type of the column
     */
    public void addColumn(String columnName, String columnType)
    {
        try {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + columnType);
        }
        catch(SQLiteException e) {}
    }

    /**
     * Queries the default table, returning a cursor to every row in the table
     * @return a cursor to all of the rows in the table
     */
    public Cursor queueAll() {
       // String[] columns = new String[] { "*" };

        Cursor cursor = db.query("tasksTable", null, null,
                null, null, null, null);
        return cursor;
    }

    /**
     * Removes all entries associated with a run number from the table, starting on a certain date
     * @param run Run number to remove
     * @param date Date to remove runs from
     */
    public void deleteRun(int run, String date) {
        db.delete("tasksTable","run=? and date > Datetime(?)",new String[]{Integer.toString(run),date});
    }

    /**
     * Empties the queue of rows to be deleted
     */
    public void emptyDeleteQueue() {
        for (String table_id : deleteQueue) {
            System.out.println("Deleting " + table_id);
            db.delete("tasksTable", "unique_table_id=" + table_id, null);
        }
    }

}
