import java.io.*;
import java.lang.reflect.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.streaming.*;


public class CustomXmlInputFormat extends FileInputFormat {

  public static final String START_TAG_KEY = "xmlinput.start";
  public static final String END_TAG_KEY = "xmlinput.end";

  @SuppressWarnings("unchecked")
  @Override
  public RecordReader<LongWritable, Text> getRecordReader(final InputSplit genericSplit,
                                      JobConf job, Reporter reporter) throws IOException {
      return new XmlRecordReader((FileSplit) genericSplit, job, reporter);
  }


  public static class XmlRecordReader implements RecordReader<LongWritable, Text> {

    private final byte[] endTag;
    private final byte[] startTag;
    private final long start;
    private final long end;
    private final FSDataInputStream fsin;
    private final DataOutputBuffer buffer = new DataOutputBuffer();
    private LongWritable currentKey;
    private Text currentValue;

    public XmlRecordReader(FileSplit split, JobConf conf, Reporter reporter) throws IOException {
      startTag = conf.get(START_TAG_KEY).getBytes("UTF-8");
      endTag = conf.get(END_TAG_KEY).getBytes("UTF-8");

      start = split.getStart();
      end = start + split.getLength();
      Path file = split.getPath();
      FileSystem fs = file.getFileSystem(conf);
      fsin = fs.open(split.getPath());
      fsin.seek(start);
    }


    public boolean next(LongWritable key, Text value) throws IOException {
      if (fsin.getPos() < end && readUntilMatch(startTag, false)) {
        try {
          buffer.write(startTag);
          if (readUntilMatch(endTag, true)) {
            key.set(fsin.getPos());
            value.set(buffer.getData(), 0, buffer.getLength());
            return true;
          }
        } finally {
          buffer.reset();
        }
      }
      return false;
    }

    public boolean readUntilMatch(byte[] match, boolean withinBlock)
        throws IOException {
      int i = 0;
      while (true) {
        int b = fsin.read();
        if (b == -1) {
          return false;
        }

        if (withinBlock && b != (byte) '\r' && b != (byte) '\n') {
          buffer.write(b);
        }

        if (b == match[i]) {
          i++;
          if (i >= match.length) {
            return true;
          }
        } else {
          i = 0;
        }

        if (!withinBlock && i == 0 && fsin.getPos() >= end) {
          return false;
        }
      }
    }

    @Override
    public float getProgress() throws IOException {
      return (fsin.getPos() - start) / (float) (end - start);
    }

    @Override
    public synchronized long getPos() throws IOException {
        return fsin.getPos();
    }

    @Override
    public LongWritable createKey() {
      return new LongWritable();
    }

    @Override
    public Text createValue() {
      return new Text();
    }

    @Override
    public synchronized void close() throws IOException {
        fsin.close();
    }

  }
}
