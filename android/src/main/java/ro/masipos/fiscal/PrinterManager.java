package ro.masipos.fiscal;


import com.datecs.fiscalprinter.SDK.FiscalException;
import com.datecs.fiscalprinter.SDK.model.DatecsFiscalDevice;
import com.datecs.fiscalprinter.SDK.model.rou.DP05_ROU;
import com.datecs.fiscalprinter.SDK.model.rou.DP150_ROU;
import com.datecs.fiscalprinter.SDK.model.rou.DP25_ROU;
import com.datecs.fiscalprinter.SDK.model.rou.DP35_ROU;
import com.datecs.fiscalprinter.SDK.model.rou.FDModelDetector;
import com.datecs.fiscalprinter.SDK.model.rou.FMP350_ROU;
import com.datecs.fiscalprinter.SDK.model.rou.WP500_ROU;
import com.datecs.fiscalprinter.SDK.model.rou.WP50_ROU;



import java.io.IOException;

public class PrinterManager {
    private static final int FPTR_CC_ROMANIA = 0x00000100;//Show messages in Romanian language
    private static final int FPTR_CC_OTHER = 0x40000000;  //Show messages in english language
    private static String sConnectorType;
    private String modelVendorName="";

    public String getModelVendorName() {
        return modelVendorName;
    }

    public DatecsFiscalDevice getFiscalDevice() {
        return fiscalDevice;
    }

    private DatecsFiscalDevice fiscalDevice;

    private static final String TAG = "PrinterManager";

    private AbstractConnector mConnector;

    public static final PrinterManager instance;

    static  {
        instance = new PrinterManager();
    }

    private PrinterManager() { }

    public static String getsConnectorType() {
        return sConnectorType;
    }

    public void init(AbstractConnector connector) throws IOException, FiscalException {
        fiscalDevice=new DatecsFiscalDevice(FPTR_CC_OTHER);
        mConnector = connector;
        sConnectorType=connector.getConnectorType();
        FDModelDetector datecsROUmodel = new FDModelDetector(mConnector.getInputStream(), mConnector.getOutputStream());
        modelVendorName = datecsROUmodel.detectConnectedModel();
        switch (modelVendorName) {
            case "FMP-350":
                DatecsFiscalDevice.setConnectedModel(new FMP350_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            case "DP-05":
                DatecsFiscalDevice.setConnectedModel(new DP05_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            case "DP-25":
                DatecsFiscalDevice.setConnectedModel(new DP25_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            case "DP-35":
                DatecsFiscalDevice.setConnectedModel(new DP35_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            case "DP-150":
                DatecsFiscalDevice.setConnectedModel(new DP150_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            case "WP-50":

                DatecsFiscalDevice.setConnectedModel(new WP50_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            case "WP-500":
                DatecsFiscalDevice.setConnectedModel(new WP500_ROU(datecsROUmodel.getTransportProtocol()));
                break;
            default:
                modelVendorName = "Unsupported model:" + modelVendorName;
                break;
        }

    }

    public void close() {
        if (mConnector != null) {
            try {
                mConnector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
