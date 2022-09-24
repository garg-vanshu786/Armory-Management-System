/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scratchnest;

import com.fazecast.jSerialComm.SerialPort;
import com.thingmagic.ReadExceptionListener;
import com.thingmagic.ReadListener;
import com.thingmagic.Reader;
import com.thingmagic.ReaderException;
import com.thingmagic.SimpleReadPlan;
import com.thingmagic.TagProtocol;
import com.thingmagic.TagReadData;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * FXML Controller class
 *
 * @author jeev8
 */
public class ArmsTabViewController implements Initializable {

    @FXML
    private ToggleGroup monitoringServiceStartStopToggleGroup;
    
    @FXML
    private Label readerConnectionStatusLabel;
    
    @FXML
    private Label readerConnectionStatusLabel1;
    
    @FXML
    private ImageView fingerPrintImageView;
    
    @FXML
    private GridPane fingerPrintGridPane;
    
    @FXML
    private Label authenticationLabel;
    
    protected static Reader rfidReader = null;
    
    volatile static StringProperty tag = new SimpleStringProperty();
    
    protected static SerialPort rfidReaderSerialPort = null;
    
    protected static AudioInputStream audioInputStream;
    
    protected  static Clip clip; 


    /**
     * Initializes the controller class.
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // TODO
        
        startRfidReaderService();
        tag.setValue("NOT READING");
        readerConnectionStatusLabel1.textProperty().bind(tag);
        fingerPrintAnimationService();
        
        try{
            Class.forName("com.mysql.jdbc.Driver");
        }catch(ClassNotFoundException e){
            System.out.println("MySQL JDBC Driver not found !!");
            return;
        }
        System.out.println("MySQL JDBC Driver Registered!");
        Connection connection = null;
        try{
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/JDBCDemo", "root", "password");
            System.out.println("SQL Connection to database established!");
 
        }catch (SQLException e){
            System.out.println("Connection Failed! Check output console");
        }
        
    }
    
    private void fingerPrintAnimationService(){
        
        Image fingerPrintStaticImage = new Image(getClass().getResourceAsStream("res/icons/fingerPrintAnimationStatic.png"));
        Image  fingerPrintAnimation = new Image(getClass().getResourceAsStream("res/icons/fingerPrintAnimation3.gif"));
        fingerPrintImageView.setImage(fingerPrintStaticImage);
        //fingerPrintImageView.fitWidthProperty().bind(fingerPrintGridPane.widthProperty());
        
        fingerPrintImageView.addEventHandler(MouseEvent.MOUSE_ENTERED,
        new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                Image  fingerPrintAnimation = new Image(getClass().getResourceAsStream("res/icons/fingerPrintAnimation3.gif"));
                fingerPrintImageView.setImage(fingerPrintAnimation);
                authenticationLabel.setText("Authenticated!!!");
            }
          });
        
        fingerPrintImageView.addEventHandler(MouseEvent.MOUSE_EXITED,
        new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                fingerPrintImageView.setImage(fingerPrintStaticImage);
                authenticationLabel.setText("");
            }
          });
        
    }
    
    @FXML
    public void stopServiceFromUiButton(ActionEvent event){
        try{
            rfidReader.stopReading();
            rfidReader.destroy();
            rfidReader = null;
            readerConnectionStatusLabel.textProperty().unbind();
            readerConnectionStatusLabel.setText("Disconnected");
        }catch(Exception e){
            System.out.println("Stop Service Exception: " + e);
        }
    }
    
    @FXML
    public void startServiceFromUiButton(ActionEvent event){
        try{
            if( (rfidReader == null) || (!rfidReader.hasContinuousReadStarted)){
                startRfidReaderService();
            }
        }catch(Exception e){
            System.out.println("Exception: " + e);
        }
    }
    
    private void startRfidReaderService(){
        
        rfidReaderConnectService connectService = new rfidReaderConnectService();
        
        rfidReaderReadingService readingService = new rfidReaderReadingService();
        
        connectService.restart();
        
        connectService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                //readerConnectionStatusLabel.textProperty().unbind();
                int[] antennaList = {1, 2};
                try {
                    rfidReader.paramSet("/reader/region/id", Reader.Region.IN);
                    SimpleReadPlan plan = new SimpleReadPlan(antennaList, TagProtocol.GEN2, true);
                    rfidReader.paramSet("/reader/read/plan", plan);
                    rfidReader.paramSet("/reader/gpio/outputList", antennaList);
                    readerConnectionStatusLabel.textProperty().unbind();
                    readerConnectionStatusLabel.textProperty().bind(readingService.messageProperty());
                    readingService.restart();
                } catch (Exception ex) {
                    System.out.println("Reader Setting Exception: " + ex);
                }
            }
        });
        
        readerConnectionStatusLabel.textProperty().bind(connectService.messageProperty());
    }
    
    private static void SimpleAudioPlayer(String filePath) throws UnsupportedAudioFileException, IOException, LineUnavailableException { 
        
        audioInputStream =  AudioSystem.getAudioInputStream(new File(filePath).getAbsoluteFile());
        clip = AudioSystem.getClip();
        clip.open(audioInputStream);
    } 
    
    private static class rfidReaderConnectService extends Service<Void> {
        
        String finalMessage = null;

        @Override
        protected Task createTask() {
  
            return new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String connectedSerialPort = rfidSerialPortDetection();
                    if(connectedSerialPort.equals("Reader Not Connected")){
                        finalMessage = "Disconnected";
                        //return connectedSerialPort;
                    }else{
                        try{
                            rfidReader = Reader.create("tmr:///" + connectedSerialPort);
                            rfidReader.connect();
                            System.out.println(rfidReader.paramGet("/reader/version/model").toString());
                            finalMessage = "Connected";
                        }catch(Exception e){
                            finalMessage = "Not able to connect";
                        }
                        //return finalMessage;
                    }
                    updateMessage(finalMessage);

                    return null;
                }
            };
        }
    }
    
    private static class rfidReaderReadingService extends Service<Void> {

        @Override
        protected Task createTask() {
  
            return new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    try{
                        ReadListener tagPrintListener = new ReadListener(){
                            
                            @Override
                            public void tagRead(Reader r, TagReadData tr){
                                System.out.println("Background read: " + tr.toString());
                                Platform.runLater(
                                    () -> {
                                        tag.setValue(tr.epcString());
                                    }
                                  );
                                try{
                                    SimpleAudioPlayer("src/com/scratchnest/res/audio/singleAlert.wav");
                                    clip.start();
                                }catch(Exception e){
                                    System.out.println("Audio Exception: " + e);
                                }
                                //updateMessage("Not Reading");

                            }
                        };
                        rfidReader.addReadListener(tagPrintListener);
                        ReadExceptionListener exceptionListener = new ReadExceptionListener(){
                            @Override
                            public void tagReadException(com.thingmagic.Reader r, ReaderException re){
                                updateMessage("Disconnected");

                            }
                        };
                        rfidReader.addReadExceptionListener(exceptionListener);
                        rfidReader.startReading();
                        updateMessage("Connected");
                    }catch(Exception e){
                        System.out.println("Reader Exception: " + e);
                        updateMessage("Disconnected");
                    }
                    return null;
                }
            };
        }
    }
    
    private static String rfidSerialPortDetection(){
        
        SerialPort[] serialPortList = SerialPort.getCommPorts();
        String finalSelectedPort = null; //final reader com port
        //int readerConnectedCount = 0; //number of reader connected
        for(int i = 0; i < serialPortList.length; i++){
            if(serialPortList[i].toString().substring(0, 3).equals("M6E")){
                finalSelectedPort = serialPortList[i].getSystemPortName();
                rfidReaderSerialPort = serialPortList[i];
                return finalSelectedPort;
            }
        }
        finalSelectedPort = "Reader Not Connected";
        return finalSelectedPort;
    }
    
}
