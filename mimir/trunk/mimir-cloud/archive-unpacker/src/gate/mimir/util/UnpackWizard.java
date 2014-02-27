package gate.mimir.util;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

public class UnpackWizard extends JFrame {
  
  protected JTextField outDirTextField;

  protected JProgressBar progressBar;
  
  protected Properties indexProperties;

  protected class SelectOutputDirectoryAction extends AbstractAction {
    
    private JFileChooser fileChooser;
    
    public SelectOutputDirectoryAction() {
      super("Select");
      putValue(SMALL_ICON, new ImageIcon(
          UnpackWizard.class.getClassLoader().getResource(
          "gate/mimir/resources/open-folder.png")));
      putValue(SHORT_DESCRIPTION, "Choose a directory where the Mímir index "
              + "should be unpacked to");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if(fileChooser == null) {
        fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      }
      int res = fileChooser.showOpenDialog(UnpackWizard.this);
      if(res == JFileChooser.APPROVE_OPTION) {
        outDirTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
      }
    }
  }

  protected class UnpackAction extends AbstractAction implements Runnable {
    
    public UnpackAction() {
      super("Extract Index");
      putValue(SMALL_ICON, new ImageIcon(
          UnpackWizard.class.getClassLoader().getResource(
          "gate/mimir/resources/extract-archive.png")));
      putValue(SHORT_DESCRIPTION, "Unpacks the index to the directory " +
      		"chosen above.");
      
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      progressBar.setValue(0);
      setEnabled(false);
      // run the actual work from a new thread
      Thread otherThread =  new Thread(this, "Unpack Thread");
      otherThread.setPriority(Thread.MIN_PRIORITY);
      otherThread.start();
    }
    
    /**
     * The unpack logic that gets executed in its own thread
     */
    public void run() {
      try{
        // check preconditions
        File outDir  = new File(outDirTextField.getText());
        if(!outDir.exists()) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(UnpackWizard.this, 
                  "The chosen directory does not exist!", 
                  "Directory Not Found Error", JOptionPane.ERROR_MESSAGE);
            }
          });
          //... and stop the worker thread
          return;
        }
        if(!outDir.isDirectory()) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(UnpackWizard.this, 
                  "The chosen file is not a directory!", 
                  "Not a Directory Error", JOptionPane.ERROR_MESSAGE);
            }
          });
          //... and stop the worker thread
          return;
        }
        if(!outDir.canWrite()) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(UnpackWizard.this, 
                  "Cannot write to the chosen directory!", 
                  "Directory Not Writeable Error", JOptionPane.ERROR_MESSAGE);
            }
          });
          //... and stop the worker thread
          return;
        }
        // all ok -> do the extraction
        File archiveDir = new File("foo.bar").getAbsoluteFile().getParentFile();
        URL baseUrl = UnpackWizard.class.getClassLoader().getResource(
            UnpackWizard.class.getName().replaceAll("\\.", "/") + ".class");
        if(baseUrl.getProtocol().equals("jar")) {
          String jarPath = baseUrl.getPath();
          jarPath = jarPath.substring(0, jarPath.indexOf('!'));
          if(jarPath.startsWith("file:")) {
            try {
              archiveDir = new File(new URI(jarPath)).getAbsoluteFile()
                  .getParentFile();
            } catch(URISyntaxException e1) {
              e1.printStackTrace(System.err);
            }  
          }
        }
        SortedMap<Integer, String> inputFileNames = new TreeMap<Integer, String>();
        for(Entry<Object, Object> anEntry : indexProperties.entrySet()) {
          String key = anEntry.getKey().toString(); 
          if(key.startsWith("archive file ")) {
            inputFileNames.put(Integer.parseInt(
                key.substring("archive file ".length())), 
                anEntry.getValue().toString());
          }
        }
        byte[] buff = new byte[64 * 1024];
        
        try {
          // check that all input files exist, and calculate the total size 
          long bytesToWrite = 0;
          long bytesReported = 0;
          long bytesWritten = 0;
          
          for(String aFileName : inputFileNames.values()) {
            File aFile = new File(archiveDir, aFileName);
            if(aFile.exists()){
              if(aFile.isFile()) {
                bytesToWrite += aFile.length();
              } else {
                final File wrongFile = aFile;
                SwingUtilities.invokeLater(new Runnable() {
                  public void run() {
                    JOptionPane.showMessageDialog(UnpackWizard.this, 
                        "File \"" + wrongFile.getAbsolutePath() + 
                        "\" is not a standard file!", 
                        "Non Standard File Error", JOptionPane.ERROR_MESSAGE);
                  }
                });
                //... and stop the worker thread
                return;             
              }
            } else {
              final File wrongFile = aFile;
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  JOptionPane.showMessageDialog(UnpackWizard.this, 
                      "File \"" + wrongFile.getAbsolutePath() + "\" does not exist!", 
                      "File Not Found Error", JOptionPane.ERROR_MESSAGE);
                }
              });
              //... and stop the worker thread
              return;              
            }
          }
          MultiFileInputStream mfis = new MultiFileInputStream(archiveDir, 
              inputFileNames.values().toArray(new String[inputFileNames.size()]));
          TarArchiveInputStream tis = new TarArchiveInputStream(
              new BufferedInputStream(mfis));
          TarArchiveEntry tarEntry = tis.getNextTarEntry();
          while(tarEntry != null) {
            File outFile = new File(outDir.getAbsolutePath() + "/" + tarEntry.getName());
            if(outFile.exists()) {
              final File wrongFile = outFile;
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  JOptionPane.showMessageDialog(UnpackWizard.this, 
                      "Was about to write out file \"" + 
                      wrongFile.getAbsolutePath() + "\" but it already " +
                      "exists.\nPlease [re]move existing files out of the way " +
                      "and try again.", 
                      "File Not Found Error", JOptionPane.ERROR_MESSAGE);
                }
              });
              //... and stop the worker thread
              return;
            }
            if(tarEntry.isDirectory()) {
              outFile.getAbsoluteFile().mkdirs();
            } else {
              outFile.getAbsoluteFile().getParentFile().mkdirs();
              OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile));
              int len = tis.read(buff, 0, buff.length);
              while(len != -1) {
                os.write(buff, 0, len);
                bytesWritten += len;
                // every 10 MB update the progress bar
                if(bytesWritten - bytesReported > (10 * 1024 * 1024)) {
                  bytesReported = bytesWritten;
                  final int progress = (int)(bytesReported * 100 / bytesToWrite);
                  SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      progressBar.setValue(progress);
                    }
                  });
                }
                // and read some more
                len = tis.read(buff, 0, buff.length);
              }
              os.close();
            }
            tarEntry = tis.getNextTarEntry();
          }
          
          long expectedCrc = 0;
          try {
            expectedCrc = Long.parseLong(indexProperties.getProperty("CRC32", "0"));
          } catch(NumberFormatException e) {
            System.err.println("Error while obtaining the expected CRC");
            e.printStackTrace(System.err);
          }
          if(mfis.getCRC() == expectedCrc) {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                progressBar.setValue(0);
                JOptionPane.showMessageDialog(UnpackWizard.this, 
                    "Extraction completed successfully!", 
                    "Done!", JOptionPane.INFORMATION_MESSAGE);
              }
            });
            // and we're done
            return;
          } else {
            System.err.println("CRC Error: was expecting " + expectedCrc + 
                " but got " + mfis.getCRC());
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                progressBar.setValue(0);
                JOptionPane.showMessageDialog(UnpackWizard.this, 
                    "CRC Error: the data extracted does not have the expected CRC!\n" +
                    "You should probably delete the extracted files, as they are " +
                    "likely to be invalid.", 
                    "CRC Error", JOptionPane.ERROR_MESSAGE);
              }
            });
            // and stop the worker thread
            return;
          }
        } catch(final IOException e) {
          e.printStackTrace(System.err);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              progressBar.setValue(0);
              JOptionPane.showMessageDialog(UnpackWizard.this, 
                  "Input/Output Error: " + e.getLocalizedMessage(), 
                  "Input/Output Error", JOptionPane.ERROR_MESSAGE);
            }
          });
          // and stop the worker thread
          return;
        }        
      } finally {
        // make sure the action gets re-enabled
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            progressBar.setValue(0);
            setEnabled(true);
          }
        });
      }
    }
  }
  
  public UnpackWizard() throws HeadlessException {
    super("Unpack Mímir Index");
    initLocalData();
    initGui();
    initListeners();
  }

  protected void initLocalData() {
    // load the index properties
    InputStream propIs =
            UnpackWizard.class.getClassLoader().getResourceAsStream(
                    "index-properties.xml");
    if(propIs != null) {
      try {
        indexProperties = new Properties();
        indexProperties.loadFromXML(propIs);
      } catch(Exception e) {
        // could not load the index properties
        System.err.println("Could not open archive properties.");
        e.printStackTrace();
        indexProperties = null;
      }
    }
  }

  protected void initGui() {
    if(indexProperties != null){
      setTitle("Index archive for index \"" + indexProperties.getProperty(
              "Index Name") + "\"");
      JPanel mainPanel = new JPanel();
      mainPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      mainPanel.setLayout(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      
      constraints.insets = new Insets(2, 2, 2, 2);
      constraints.gridx = 0;
      constraints.gridy = 0;
      constraints.weightx = 0;
      constraints.weighty = 0;
      
      //logo
      constraints.anchor = GridBagConstraints.CENTER;
      constraints.gridheight = 4;
      ImageIcon icon = new ImageIcon(UnpackWizard.class.getClassLoader()
          .getResource("gate/mimir/resources/logo-mimir-archive.png"));
      mainPanel.add(new JLabel(icon), constraints);
      // label
      constraints.gridx = 1;
      constraints.gridheight = 1;
      constraints.anchor = GridBagConstraints.LINE_START;
      mainPanel.add(new JLabel("Output directory:"), constraints);
      
      // open button 
      constraints.gridx = 2;
      constraints.gridheight = 2;
      mainPanel.add(new JButton(new SelectOutputDirectoryAction()), constraints);
      
      // text field
      constraints.gridy = 1;
      constraints.gridx = 1;
      constraints.gridheight = 1;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1;
      outDirTextField = new JTextField(new File("").getParent(), 30);
      mainPanel.add(outDirTextField, constraints);
      constraints.weightx = 0;
      
      // unpack button
      constraints.gridy = 2;
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      constraints.anchor  = GridBagConstraints.CENTER;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      mainPanel.add(new JButton(new UnpackAction()), constraints);
      
      //progress bar
      constraints.gridy = 3;
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      progressBar = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
      progressBar.setValue(0);
      mainPanel.add(progressBar, constraints);
      getContentPane().add(mainPanel, BorderLayout.CENTER);      
    } else {
      setTitle("Invalid JAR file");
      JLabel errorLabel = new JLabel("<html>Could not open archive properties. <br />" +
      "Was there an error during download?</html>");
      errorLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      getContentPane().add(errorLabel, BorderLayout.CENTER);
    }

    
    


    setIconImage(new ImageIcon(UnpackWizard.class.getClassLoader().getResource(
          "gate/mimir/resources/extract-archive.png")).getImage());
    pack();
    setMinimumSize(getPreferredSize());
    // center the window
    setLocationRelativeTo(null);
  }

  protected void initListeners() {
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  }

  public static void main(String[] args) {
    UnpackWizard unpackWiz = new UnpackWizard();
    unpackWiz.setVisible(true);
  }
}
