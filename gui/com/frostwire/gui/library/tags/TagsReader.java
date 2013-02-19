package com.frostwire.gui.library.tags;

import java.io.File;

public class TagsReader {

    private final File file;

    public TagsReader(File file) {
        this.file = file;
    }

    public TagsData parse() {
        TagsParser parser = new TagsParserFactory().getInstance(file);

        TagsData data = parser.parse();

        // fallback to mplayer parsing
        if (data == null || isEmpty(data)) {
            data = new MPlayerParser(file).parse();
        }

        return data;
    }

    private boolean isEmpty(TagsData data) {
        return false; // default behavior for now
    }
}