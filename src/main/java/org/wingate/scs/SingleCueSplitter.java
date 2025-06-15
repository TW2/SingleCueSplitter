package org.wingate.scs;

import com.formdev.flatlaf.FlatDarkLaf;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.PointerPointer;
import org.wingate.scs.lib.CD;
import org.wingate.scs.lib.Track;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;

public class SingleCueSplitter {

    private final static String VERSION = "1.0";

    public static void main(String[] args) {
        EventQueue.invokeLater(()->{
            System.out.println("SingleCueSplitter is about to start...");

            try{
                FlatDarkLaf.setup();
                MainFrame mf = new MainFrame();
                mf.setTitle("SingleCueSplitter :: SCueS " + VERSION);
                mf.setLocationRelativeTo(null);
                mf.setVisible(true);
            }catch(HeadlessException _){
                System.err.println("HeadlessException has occurred, the program stops!");
                System.exit(1);
            }

            System.out.println("SingleCueSplitter has started!");
        });
    }

    public static class MainFrame extends JFrame implements Runnable {

        public enum MusicFormat {
            WAV(".wav"),
            MP3(".mp3"),
            AAC(".m4a"),
            FLAC(".flac"),
            OPUS(".opus"),
            OGG(".ogg"),
            WMA(".wma");

            final String extension;

            MusicFormat(String extension){
                this.extension = extension;
            }

            public String getExtension(){
                return extension;
            }
        }

        private final List<String> files;

        private volatile Thread th = null;
        private int totalTracks = 0;

        private String lastData = null;
        private final List<CD> disks;

        private final JComboBox<MusicFormat> cbFormat;
        private final JTextField tfInputFolder;
        private final JButton btnChooseInput;
        private final JTextField tfOutputFolder;
        private final JButton btnChooseOutput;
        private final JButton btnProcess;
        private final JProgressBar pCD;
        private final JProgressBar pAll;

        public MainFrame() throws HeadlessException{
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            files = new ArrayList<>();
            disks = new ArrayList<>();

            cbFormat = new JComboBox<>();
            DefaultComboBoxModel<MusicFormat> cbModelFormat = new DefaultComboBoxModel<>();
            cbModelFormat.addAll(Arrays.asList(MusicFormat.values()));
            cbFormat.setModel(cbModelFormat);
            cbFormat.setSelectedIndex(1);
            cbFormat.setPreferredSize(new Dimension(580, cbFormat.getHeight()));

            JLabel lblFormat = new JLabel("   Choose a format :");

            JPanel topPanel = new JPanel(new BorderLayout(4,4));
            topPanel.add(lblFormat, BorderLayout.CENTER);
            topPanel.add(cbFormat, BorderLayout.EAST);

            tfInputFolder = new JTextField();
            JLabel lblInputFolder = new JLabel("   Input folder :");
            JPanel panInputFolder1 = new JPanel(new BorderLayout(4,4));
            panInputFolder1.add(tfInputFolder, BorderLayout.CENTER);
            panInputFolder1.add(lblInputFolder, BorderLayout.WEST);
            btnChooseInput = new JButton("...");
            JPanel panInputFolder2 = new JPanel(new BorderLayout(4,4));
            panInputFolder2.add(panInputFolder1, BorderLayout.CENTER);
            panInputFolder2.add(btnChooseInput, BorderLayout.EAST);
            lblInputFolder.setPreferredSize(new Dimension(200, lblInputFolder.getHeight()));

            tfOutputFolder = new JTextField();
            JLabel lblOutputFolder = new JLabel("   Output folder :");
            JPanel panOutputFolder1 = new JPanel(new BorderLayout(4,4));
            panOutputFolder1.add(tfOutputFolder, BorderLayout.CENTER);
            panOutputFolder1.add(lblOutputFolder, BorderLayout.WEST);
            btnChooseOutput = new JButton("...");
            JPanel panOutputFolder2 = new JPanel(new BorderLayout(4,4));
            panOutputFolder2.add(panOutputFolder1, BorderLayout.CENTER);
            panOutputFolder2.add(btnChooseOutput, BorderLayout.EAST);
            lblOutputFolder.setPreferredSize(new Dimension(200, lblOutputFolder.getHeight()));

            btnProcess = new JButton("Extract");

            pCD = new JProgressBar(0, 100);
            pCD.setValue(0);
            pAll = new JProgressBar(0, 100);
            pAll.setValue(0);

            JPanel panInOut = new JPanel(new GridLayout(6, 1, 4, 4));
            panInOut.add(topPanel);
            panInOut.add(panInputFolder2);
            panInOut.add(panOutputFolder2);
            panInOut.add(btnProcess);
            panInOut.add(pCD);
            panInOut.add(pAll);

            JFileChooser fcIn = new JFileChooser();
            fcIn.setAcceptAllFileFilterUsed(false);
            fcIn.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fcIn.setDialogTitle("Choose an input top folder");

            btnChooseInput.addActionListener((_)->{
                int z = fcIn.showOpenDialog(this);
                if(z == JFileChooser.APPROVE_OPTION){
                    tfInputFolder.setText(fcIn.getSelectedFile().getAbsolutePath());
                }
            });

            JFileChooser fcOut = new JFileChooser();
            fcOut.setAcceptAllFileFilterUsed(false);
            fcOut.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fcOut.setDialogTitle("Choose an output top folder");

            btnChooseOutput.addActionListener((_)->{
                int z = fcOut.showSaveDialog(this);
                if(z == JFileChooser.APPROVE_OPTION){
                    tfOutputFolder.setText(fcOut.getSelectedFile().getAbsolutePath());
                }
            });

            btnProcess.addActionListener((_)->{
                File inputFolder = new File(tfInputFolder.getText());
                if(!inputFolder.exists()) return;
                File outputFolder = new File(tfOutputFolder.getText());
                if(!outputFolder.exists()) return;

                // Disable manual change
                paralyse(true);

                // Fill the files
                files.addAll(populate(new ArrayList<>(), inputFolder));
                if(files.isEmpty()){
                    paralyse(false);
                    return;
                }

                // Fill the disks
                for(String path : files){
                    CD cd = cueReader(new File(path));
                    if(cd != null){
                        boolean add = true;
                        for(CD x : disks){
                            if(x.getUid().equals(cd.getUid())){
                                add = false;
                                break;
                            }
                        }
                        if(add){
                            disks.add(cd);
                        }
                    }
                }
                if(disks.isEmpty()){
                    paralyse(false);
                    return;
                }

                // Ready
                launch();
            });

            getContentPane().setLayout(new BorderLayout(4,4));
            getContentPane().add(panInOut, BorderLayout.CENTER);

            setPreferredSize(new Dimension(800, 250));
            pack();
        }

        private void paralyse(boolean v){
            tfInputFolder.setEditable(!v);
            tfOutputFolder.setEditable(!v);
            btnChooseInput.setEnabled(!v);
            btnChooseOutput.setEnabled(!v);
            cbFormat.setEditable(!v);
            cbFormat.setEnabled(!v);
            btnProcess.setEnabled(!v);
        }

        private List<String> populate(List<String> output, File dir){
            if(dir.isDirectory()){
                for(File f : Objects.requireNonNull(dir.listFiles())){
                    if(f.isDirectory()){
                        output.addAll(populate(output, f));
                    }else{
                        if(f.getName().endsWith(".cue")){
                            output.add(f.getAbsolutePath());
                        }
                    }
                }
            }
            return output;
        }

        private void launch(){
            th = new Thread(this);
            th.start();
        }

        private void doJob(){
            // Prefetch files
            //---------------
            for(CD cd : disks){
                totalTracks += cd.getTracks().size();
            }
            pAll.setMinimum(0);
            pAll.setMaximum(totalTracks);
            pAll.setValue(0);

            int k = 0;
            for(int i=0; i<disks.size(); i++){
                CD cd = disks.get(i);
                pCD.setMinimum(0);
                pCD.setMaximum(cd.getTracks().size());
                pCD.setValue(0);
                for(int j=0; j<cd.getTracks().size(); j++){
                    try {
                        FFMPEGWriter(cd, cd.getTracks().get(j));
                        System.out.println(i + "-" + j);
                        System.out.println(cd.getTracks().get(j).getStop());
                        pCD.setValue(j + 1);
                        pAll.setValue(k + j + 1);
                        System.out.printf("%d/%d (Disk%d) done\n", k+j+1, totalTracks, i+1);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                k += cd.getTracks().size();
            }
        }

        private CD cueReader(File file){
            try(FileReader fr = new FileReader(file);
                    BufferedReader br = new BufferedReader(fr)) {
                String line;
                CD cd = new CD(file.getAbsolutePath());
                Track tr = null;
                while((line = br.readLine()) != null){
                    String r = line.toLowerCase();
                    if(r.startsWith("rem genre")){
                        cd.setGenre(line.substring(10));
                    }else if(r.startsWith("rem date")){
                        cd.setYear(line.substring(9));
                    }else if(r.startsWith("rem discid")){
                        cd.setUid(line.substring(11));
                    }else if(r.startsWith("rem comment")){
                        cd.setComment(getBritish(line));
                    }else if(r.startsWith("performer")){
                        cd.setPerformer(getBritish(line));
                    }else if(r.startsWith("title")){
                        String str = getBritish(line);
                        if(lastData != null && lastData.equals(str)) return null;
                        cd.setTitle(str);
                        lastData = str;
                    }else if(r.startsWith("file")){
                        cd.setAudioFile(file.getParent() + File.separator + getBritish(line));
                    }else if(r.trim().startsWith("track ") && r.endsWith(" audio")){
                        if(tr != null) cd.getTracks().add(tr);
                        tr = new Track();
                        tr.setNumber(getTrackNumber(line));
                    }else if(r.trim().startsWith("title") && tr != null){
                        tr.setTitle(getBritish(line));
                    }else if(r.trim().startsWith("performer") && tr != null){
                        tr.setPerformer(getBritish(line));
                    }else if(r.trim().startsWith("index")){
                        tr.setStart(ffTime(line.substring(line.indexOf("index") + 14)));
                        if(!cd.getTracks().isEmpty()){
                            cd.getTracks().getLast().setStop(tr.getStart());
                        }
                    }
                }
                if(tr != null){
                    tr.setStop(ffTime(getDuration(cd)));
                    cd.getTracks().add(tr);
                }
                return cd;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private String getBritish(String line){
            return line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""));
        }

        private int getTrackNumber(String line){
            return Integer.parseInt(
                    line.trim().substring(line.indexOf("track") + 7, line.lastIndexOf(" ") - 2));
        }

        private void FFMPEGWriter(CD cd, Track track) throws IOException, InterruptedException {
            // Prepare output folder
            String input = new File(cd.getDiskLocation()).getParent();
            String qCD = "";
            if(input.contains(File.separator + "CD")
                    && input.length() - input.lastIndexOf(File.separator + "CD") <= 6){
                qCD = input.substring(input.lastIndexOf(File.separator) + 1);
            }

            String output = tfOutputFolder.getText() + File.separator
                    + replaceUnauthorized(cd.getTitle()) + File.separator
                    + (qCD.isEmpty() ? "" : qCD + File.separator);

            File fOut = new File(output);
            boolean v = fOut.mkdirs();
            System.out.println(v ?
                    "File \"" + fOut.getAbsolutePath() + "\" created!" :
                    "File \"" + fOut.getAbsolutePath() + "\" already existing!");
            System.out.println("Start: " + ffTime(track.getStart()));
            System.out.println("End: " + ffTime(track.getStop()));

            String ffmpeg = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg,
                    /* START TIME */
                    "-ss",
                    String.format("\"%s\"", ffTime(track.getStart())),
                    /* END TIME */
                    "-to",
                    String.format("\"%s\"", ffTime(track.getStop())),
                    /* INPUT */
                    "-i",
                    String.format("\"%s\"",
                            cd.getAudioFile()),
                    "-metadata",
                    String.format("\"genre=%s\"",
                            cd.getGenre()),
                    "-metadata",
                    String.format("\"date=%s\"",
                            cd.getYear()),
                    "-metadata",
                    String.format("\"performer=%s\"",
                            cd.getPerformer()),
                    "-metadata",
                    String.format("\"album=%s\"",
                            cd.getTitle()),
                    "-metadata",
                    String.format("\"title=%s\"",
                            track.getTitle()),
                    "-metadata",
                    String.format("\"artist=%s\"",
                            track.getPerformer()),
                    "-metadata",
                    String.format("\"track=%d\"",
                            track.getNumber()),
                    /* OUTPUT */
                    String.format("\"%s%03d - %s [%s]%s\"",
                            output,
                            track.getNumber(),
                            replaceUnauthorized(track.getTitle()),
                            replaceUnauthorized(track.getPerformer()),
                            ((MusicFormat) Objects.requireNonNull(cbFormat.getSelectedItem())).getExtension())
            );
            pb.inheritIO().start().waitFor();
        }

        private String getDuration(CD cd){
            int ret, i, a_stream_idx = -1;

            AVFormatContext fmt_ctx = new AVFormatContext(null);
            ret = avformat_open_input(fmt_ctx, cd.getAudioFile(), null, null);
            if (ret < 0) {
                return "00:00:00.000";
            }

            // i dont know but without this function, sws_getContext does not work
            if (avformat_find_stream_info(fmt_ctx, (PointerPointer)null) < 0) {
                avformat_close_input(fmt_ctx);
                return "00:00:00.000";
            }

            av_dump_format(fmt_ctx, 0, cd.getAudioFile(), 0);

            for (i = 0; i < fmt_ctx.nb_streams(); i++) {
                if (fmt_ctx.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
                    a_stream_idx = i;
                    break;
                }
            }

            if (a_stream_idx == -1) {
                avformat_close_input(fmt_ctx);
                return "00:00:00.000";
            }

            AVCodecContext codec_ctx = avcodec_alloc_context3(null);
            avcodec_parameters_to_context(codec_ctx, fmt_ctx.streams(a_stream_idx).codecpar());

            AVCodec codec = avcodec_find_decoder(codec_ctx.codec_id());
            if (codec == null) {
                avcodec_free_context(codec_ctx);
                avformat_close_input(fmt_ctx);
                return "00:00:00.000";
            }
            ret = avcodec_open2(codec_ctx, codec, (PointerPointer)null);
            if (ret < 0) {
                avcodec_close(codec_ctx);
                avcodec_free_context(codec_ctx);
                avformat_close_input(fmt_ctx);
                return "00:00:00.000";
            }

            double seconds = (double)fmt_ctx.streams(a_stream_idx).duration() *
                    (double)codec_ctx.time_base().num() / (double)codec_ctx.time_base().den();

            avcodec_close(codec_ctx);
            avcodec_free_context(codec_ctx);
            avformat_close_input(fmt_ctx);

            return ffTime(Double.toString(seconds));
        }

        private String ffTime(String time){
            if(time != null && time.matches("\\d{2}:\\d{2}:\\d{2}.\\d{3}")){
                return time;
            }
            long ms = 0L;
            if(time != null){
                if(time.matches("\\d{2}:\\d{2}:\\d{2}")){
                    Pattern p = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})");
                    Matcher m = p.matcher(time);
                    if(m.find()){
                        long s = TimeUnit.MINUTES.toSeconds(Long.parseLong(m.group(1)));
                        s += TimeUnit.SECONDS.toSeconds(Long.parseLong(m.group(2)));
                        ms = s * 1000L + (Long.parseLong(m.group(3)) * 10L);
                    }
                }else if(time.matches("\\d+.\\d+")){
                    ms = (long)(Double.parseDouble(time) * 1000L);
                }
            }

            long HH = TimeUnit.MILLISECONDS.toHours(ms);
            long MM = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
            long SS = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
            long MS = TimeUnit.MILLISECONDS.toMillis(ms) % 1000;
            return String.format("%02d:%02d:%02d.%03d", HH, MM, SS, MS);
        }

        private String replaceUnauthorized(String s){
            String[] t = new String[]{"<", ">", ":", "\"", "/", "\\", "|", "?", "*"};
            for(String x : t){
                s = s.replace(x, " ");
            }
            return s;
        }

        @Override
        public void run() {
            doJob();
            th.interrupt();
            paralyse(false);
            pAll.setValue(0);
            pCD.setValue(0);
        }

    }
}
