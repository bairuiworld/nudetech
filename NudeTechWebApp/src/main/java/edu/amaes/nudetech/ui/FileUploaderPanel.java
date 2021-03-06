package edu.amaes.nudetech.ui;

import com.vaadin.terminal.FileResource;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Upload.FinishedEvent;
import com.vaadin.ui.Upload.Receiver;
import com.vaadin.ui.Upload.StartedEvent;
import com.vaadin.ui.*;
import com.vaadin.ui.Window.CloseEvent;
import com.vaadin.ui.Window.CloseListener;
import edu.amaes.nudetech.algo.nuditydetect.ColoredImageNudityDetector;
import edu.amaes.nudetech.algo.nuditydetect.ImageNudityDetector;
import edu.amaes.nudetech.algo.video.frameextract.VideoFrameExtractor;
import edu.amaes.nudetech.algo.video.frameextract.VideoFrameExtractorImpl;
import java.awt.Image;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.activation.MimetypesFileTypeMap;
import javax.imageio.ImageIO;
import org.apache.log4j.Logger;

/**
 *
 * @author Angelo Balaguer
 */
public class FileUploaderPanel extends VerticalLayout {

    private final static Logger LOGGER = Logger.getLogger(FileUploaderPanel.class);
    private final static String uploadDir = "C:/test/";
    private final static String framesDir = "C:/test/frames/";
    private Label fileName = new Label();
    private Label textualProgress = new Label();
    private ProgressIndicator progressIndicator = new ProgressIndicator();
    private FileUploaderPanel.LineBreakCounter counter = new FileUploaderPanel.LineBreakCounter();
    private Upload upload = new Upload(null, counter);
    private Window imageModal;
    private Button open;
    private Embedded imageNude;
    private Embedded imageNotNude;
    private List<File> nudeFrames;

    public FileUploaderPanel() {
        setSpacing(true);
        setMargin(true);

        Label uploadLabel = new Label();
        uploadLabel.setCaption("Upload Image or Video");
        uploadLabel.setIcon(new ThemeResource("img/upload_icon.gif"));
        addComponent(uploadLabel);

        // make analyzing start immediatedly when file is selected
        upload.setImmediate(true);
        upload.setButtonCaption("Upload File");
        addComponent(upload);

        final Button cancelProcessing = new Button("Cancel Upload");
        cancelProcessing.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                upload.interruptUpload();
            }
        });
        cancelProcessing.setVisible(false);
        cancelProcessing.setStyleName("small");

        Panel uploadDetailsPanel = new Panel("Upload Details");
        uploadDetailsPanel.isImmediate();
        uploadDetailsPanel.setWidth("100%");
        uploadDetailsPanel.setHeight("50%");

        FormLayout uploadDetailsPanelLayout = new FormLayout();
        uploadDetailsPanelLayout.setMargin(true);
        uploadDetailsPanelLayout.isImmediate();
        uploadDetailsPanelLayout.setMargin(false, false, false, true);
        uploadDetailsPanel.setContent(uploadDetailsPanelLayout);

        HorizontalLayout progressLayout = new HorizontalLayout();
        progressLayout.setSpacing(true);
        progressLayout.setCaption("Upload Progress");

        progressIndicator.setVisible(false);
        progressLayout.addComponent(progressIndicator);
        progressLayout.addComponent(cancelProcessing);
        uploadDetailsPanelLayout.addComponent(progressLayout);

        fileName.setCaption("File name");

        uploadDetailsPanelLayout.addComponent(fileName);

        imageNude = new Embedded("", new ThemeResource("img/x_nude.jpg"));
        imageNude.setType(Embedded.TYPE_IMAGE);

        imageNotNude = new Embedded("", new ThemeResource("img/check_notnude.jpg"));
        imageNotNude.setType(Embedded.TYPE_IMAGE);

        uploadDetailsPanelLayout.addComponent(imageNude);
        uploadDetailsPanelLayout.addComponent(imageNotNude);

        imageNude.setVisible(false);
        imageNotNude.setVisible(false);
        textualProgress.setVisible(false);

        uploadDetailsPanelLayout.addComponent(textualProgress);

        CloseListener cl = new CloseListener() {

            @Override
            public void windowClose(CloseEvent e) {
                for(File detectedFrames: nudeFrames) {
                        detectedFrames.delete();
                    }
                
                fileName.setValue("");
            }
        };
        
        imageModal = new Window();
        imageModal.addListener(cl);
        imageModal.setWidth("40%");
        imageModal.setHeight("80%");
        imageModal.setModal(true);

        GridLayout modalLayout = new GridLayout(3,5);
        modalLayout.setSpacing(true);
        modalLayout.setSizeFull();
        imageModal.addComponent(modalLayout);
        
        open = new Button("View Detected Frames",
                new Button.ClickListener() {
                    @Override
                    public void buttonClick(ClickEvent event) {
                        if (imageModal.getParent() != null) {
                            // window is already showing
                            getWindow().showNotification(
                                    "Window is already open");
                        } else {
                            // Open the subwindow by adding it to the parent
                            // window
                            getWindow().addWindow(imageModal);
                        }
                    }
                });


        addComponent(uploadDetailsPanel);
        addComponent(open);
        open.setVisible(false);

        upload.addListener(new Upload.StartedListener() {

            @Override
            public void uploadStarted(StartedEvent event) {
                // this method gets called immediatedly after upload is
                // started
                progressIndicator.setValue(0f);
                progressIndicator.setVisible(true);
                progressIndicator.setPollingInterval(100); // hit server frequantly to get
                textualProgress.setVisible(true);
                // updates to client
                fileName.setValue(event.getFilename());
                imageNude.setVisible(false);
                imageNotNude.setVisible(false);
                cancelProcessing.setVisible(true);
            }
        });

        upload.addListener(new Upload.ProgressListener() {

            @Override
            public void updateProgress(long readBytes, long contentLength) {
                // this method gets called several times during the update
                progressIndicator.setValue(new Float(readBytes / (float) contentLength));
                textualProgress.setValue("Processed " + readBytes
                        + " bytes of " + contentLength);
            }
        });

        upload.addListener(new Upload.FinishedListener() {

            @Override
            public void uploadFinished(FinishedEvent event) {
                String mimetype = new MimetypesFileTypeMap().getContentType(counter.getUploadedFile());
                String type = mimetype.split("/")[0];
                if (type.equals("image")) {
                    try {
                        Image inputImage = ImageIO.read(counter.getUploadedFile());
                        ImageNudityDetector nudityDetector = new ColoredImageNudityDetector();
                        boolean isNude = nudityDetector.isImageNude(inputImage);
                        LOGGER.debug("file: " + counter.getUploadedFile().getName() + ", isNude: " + isNude);
                        if (isNude) {
                            imageNude.setVisible(true);
                        } else {
                            imageNotNude.setVisible(true);
                        }
                        counter.getUploadedFile().delete();
                    } catch (IOException e) {
                    }
                } else {
                    VideoFrameExtractor frameExtractor = new VideoFrameExtractorImpl();
                    frameExtractor.extractFrames(counter.getUploadedFile().getAbsolutePath(), framesDir);
                    counter.getUploadedFile().delete();
                    File folder = new File(framesDir);
                    File[] frames = folder.listFiles();
                    nudeFrames = new ArrayList<File>();
                    double numNude = 0;
                    double numFrames = 0;
                    for (File frame : frames) {
                        try {
                            Image inputImage = ImageIO.read(frame);
                            ImageNudityDetector nudityDetector = new ColoredImageNudityDetector();
                            boolean isNude = nudityDetector.isImageNude(inputImage);
                            LOGGER.debug("file: " + frame.getName() + ", isNude: " + isNude);
                            numFrames++;
                            if (isNude) {
                                nudeFrames.add(frame);
                                numNude++;
                            } else {
                                frame.delete();
                            }
                        } catch (IOException e) {
                        }
                    }
                    imageModal.removeAllComponents();
                    Collections.shuffle(nudeFrames);
                    if (nudeFrames.size() > 10) {
                        for (int x = 0; x < 10; x++) {
                            final FileResource imageResource = new FileResource(nudeFrames.get(x), getApplication());
                            imageModal.addComponent(new Embedded("",imageResource));
                        }
                    } 
                    else if(nudeFrames.size()>1 && nudeFrames.size()<10) {
                        for (int x = 0; x < nudeFrames.size(); x++) {
                            final FileResource imageResource = new FileResource(nudeFrames.get(x), getApplication());
                            imageModal.addComponent(new Embedded("",imageResource));
                        }  
                    }
                    else {
                         Embedded checkIMG = new Embedded("", new ThemeResource("img/check_notnude.jpg"));
                         checkIMG.setType(Embedded.TYPE_IMAGE);
                         imageModal.addComponent(checkIMG);
                    }
                    imageModal.setCaption("VIDEO IS " + ((numNude/numFrames)*100) + "% NUDE");
                    getWindow().addWindow(imageModal);
                }
                progressIndicator.setVisible(false);
                textualProgress.setVisible(false);
                cancelProcessing.setVisible(false);
            }
        });

    }

    public static class LineBreakCounter implements Receiver {

        private File uploadedFile = null;
        private String fileName;
        private String mtype;
        
        @Override
        public OutputStream receiveUpload(String filename, String MIMEType) {
            
            fileName = filename;
            mtype = MIMEType;
            uploadedFile = new File(uploadDir + fileName);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(uploadedFile);
            } catch (IOException e) {
            }
            return fos;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMimeType() {
            return mtype;
        }

        public File getUploadedFile() {
            return uploadedFile;
        }
    }
}