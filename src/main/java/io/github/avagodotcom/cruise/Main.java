package io.github.avagodotcom.cruise;

import io.github.avagodotcom.cruise.cli.Cli;
import io.github.avagodotcom.cruise.persistence.Db;
import io.github.avagodotcom.cruise.persistence.Schema;


public class Main {
    public static void main(String[] args) {
        try {
            Db db = new Db("jdbc:sqlite:./demo.db");
            new Schema(db).init();
            new Cli(db).run(args);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}