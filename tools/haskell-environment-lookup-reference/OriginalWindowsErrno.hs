module OriginalWindowsErrno where
import GHC.IO.Exception (IOException(..),IOErrorType(..))
import System.IO (Handle)
import Foreign.C.Types (CInt)
import System.IO.Unsafe
newtype Errno = Errno CInt deriving (Eq)
errnoToString :: Errno -> IO String
errnoToString _ = pure "ignored errno string"
eOK :: Errno
eOK = Errno (0)
e2BIG :: Errno
e2BIG = Errno (7)
eACCES :: Errno
eACCES = Errno (13)
eADDRINUSE :: Errno
eADDRINUSE = Errno (100)
eADDRNOTAVAIL :: Errno
eADDRNOTAVAIL = Errno (101)
eADV :: Errno
eADV = Errno (-1)
eAFNOSUPPORT :: Errno
eAFNOSUPPORT = Errno (102)
eAGAIN :: Errno
eAGAIN = Errno (11)
eALREADY :: Errno
eALREADY = Errno (103)
eBADF :: Errno
eBADF = Errno (9)
eBADMSG :: Errno
eBADMSG = Errno (104)
eBADRPC :: Errno
eBADRPC = Errno (-1)
eBUSY :: Errno
eBUSY = Errno (16)
eCHILD :: Errno
eCHILD = Errno (10)
eCOMM :: Errno
eCOMM = Errno (-1)
eCONNABORTED :: Errno
eCONNABORTED = Errno (106)
eCONNREFUSED :: Errno
eCONNREFUSED = Errno (107)
eCONNRESET :: Errno
eCONNRESET = Errno (108)
eDEADLK :: Errno
eDEADLK = Errno (36)
eDESTADDRREQ :: Errno
eDESTADDRREQ = Errno (109)
eDIRTY :: Errno
eDIRTY = Errno (-1)
eDOM :: Errno
eDOM = Errno (33)
eDQUOT :: Errno
eDQUOT = Errno (-1)
eEXIST :: Errno
eEXIST = Errno (17)
eFAULT :: Errno
eFAULT = Errno (14)
eFBIG :: Errno
eFBIG = Errno (27)
eFTYPE :: Errno
eFTYPE = Errno (-1)
eHOSTDOWN :: Errno
eHOSTDOWN = Errno (-1)
eHOSTUNREACH :: Errno
eHOSTUNREACH = Errno (110)
eIDRM :: Errno
eIDRM = Errno (111)
eILSEQ :: Errno
eILSEQ = Errno (42)
eINPROGRESS :: Errno
eINPROGRESS = Errno (112)
eINTR :: Errno
eINTR = Errno (4)
eINVAL :: Errno
eINVAL = Errno (22)
eIO :: Errno
eIO = Errno (5)
eISCONN :: Errno
eISCONN = Errno (113)
eISDIR :: Errno
eISDIR = Errno (21)
eLOOP :: Errno
eLOOP = Errno (114)
eMFILE :: Errno
eMFILE = Errno (24)
eMLINK :: Errno
eMLINK = Errno (31)
eMSGSIZE :: Errno
eMSGSIZE = Errno (115)
eMULTIHOP :: Errno
eMULTIHOP = Errno (-1)
eNAMETOOLONG :: Errno
eNAMETOOLONG = Errno (38)
eNETDOWN :: Errno
eNETDOWN = Errno (116)
eNETRESET :: Errno
eNETRESET = Errno (117)
eNETUNREACH :: Errno
eNETUNREACH = Errno (118)
eNFILE :: Errno
eNFILE = Errno (23)
eNOBUFS :: Errno
eNOBUFS = Errno (119)
eNODATA :: Errno
eNODATA = Errno (120)
eNODEV :: Errno
eNODEV = Errno (19)
eNOENT :: Errno
eNOENT = Errno (2)
eNOEXEC :: Errno
eNOEXEC = Errno (8)
eNOLCK :: Errno
eNOLCK = Errno (39)
eNOLINK :: Errno
eNOLINK = Errno (121)
eNOMEM :: Errno
eNOMEM = Errno (12)
eNOMSG :: Errno
eNOMSG = Errno (122)
eNONET :: Errno
eNONET = Errno (-1)
eNOPROTOOPT :: Errno
eNOPROTOOPT = Errno (123)
eNOSPC :: Errno
eNOSPC = Errno (28)
eNOSR :: Errno
eNOSR = Errno (124)
eNOSTR :: Errno
eNOSTR = Errno (125)
eNOSYS :: Errno
eNOSYS = Errno (40)
eNOTBLK :: Errno
eNOTBLK = Errno (-1)
eNOTCONN :: Errno
eNOTCONN = Errno (126)
eNOTDIR :: Errno
eNOTDIR = Errno (20)
eNOTEMPTY :: Errno
eNOTEMPTY = Errno (41)
eNOTSOCK :: Errno
eNOTSOCK = Errno (128)
eNOTTY :: Errno
eNOTTY = Errno (25)
eNXIO :: Errno
eNXIO = Errno (6)
eOPNOTSUPP :: Errno
eOPNOTSUPP = Errno (130)
ePERM :: Errno
ePERM = Errno (1)
ePFNOSUPPORT :: Errno
ePFNOSUPPORT = Errno (-1)
ePIPE :: Errno
ePIPE = Errno (32)
ePROCLIM :: Errno
ePROCLIM = Errno (-1)
ePROCUNAVAIL :: Errno
ePROCUNAVAIL = Errno (-1)
ePROGMISMATCH :: Errno
ePROGMISMATCH = Errno (-1)
ePROGUNAVAIL :: Errno
ePROGUNAVAIL = Errno (-1)
ePROTO :: Errno
ePROTO = Errno (134)
ePROTONOSUPPORT :: Errno
ePROTONOSUPPORT = Errno (135)
ePROTOTYPE :: Errno
ePROTOTYPE = Errno (136)
eRANGE :: Errno
eRANGE = Errno (34)
eREMCHG :: Errno
eREMCHG = Errno (-1)
eREMOTE :: Errno
eREMOTE = Errno (-1)
eROFS :: Errno
eROFS = Errno (30)
eRPCMISMATCH :: Errno
eRPCMISMATCH = Errno (-1)
eRREMOTE :: Errno
eRREMOTE = Errno (-1)
eSHUTDOWN :: Errno
eSHUTDOWN = Errno (-1)
eSOCKTNOSUPPORT :: Errno
eSOCKTNOSUPPORT = Errno (-1)
eSPIPE :: Errno
eSPIPE = Errno (29)
eSRCH :: Errno
eSRCH = Errno (3)
eSRMNT :: Errno
eSRMNT = Errno (-1)
eSTALE :: Errno
eSTALE = Errno (-1)
eTIME :: Errno
eTIME = Errno (137)
eTIMEDOUT :: Errno
eTIMEDOUT = Errno (138)
eTOOMANYREFS :: Errno
eTOOMANYREFS = Errno (-1)
eTXTBSY :: Errno
eTXTBSY = Errno (139)
eUSERS :: Errno
eUSERS = Errno (-1)
eWOULDBLOCK :: Errno
eWOULDBLOCK = Errno (140)
eXDEV :: Errno
eXDEV = Errno (18)
errnoToIOError  :: String       -- ^ the location where the error occurred
                -> Errno        -- ^ the error number
                -> Maybe Handle -- ^ optional handle associated with the error
                -> Maybe String -- ^ optional filename associated with the error
                -> IOError
errnoToIOError loc errno maybeHdl maybeName = unsafePerformIO $ do
    str <- errnoToString errno
    return (IOError maybeHdl errType loc str (Just errno') maybeName)
    where
    Errno errno' = errno
    errType
        | errno == eOK             = OtherError
        | errno == e2BIG           = ResourceExhausted
        | errno == eACCES          = PermissionDenied
        | errno == eADDRINUSE      = ResourceBusy
        | errno == eADDRNOTAVAIL   = UnsupportedOperation
        | errno == eADV            = OtherError
        | errno == eAFNOSUPPORT    = UnsupportedOperation
        | errno == eAGAIN          = ResourceExhausted
        | errno == eALREADY        = AlreadyExists
        | errno == eBADF           = InvalidArgument
        | errno == eBADMSG         = InappropriateType
        | errno == eBADRPC         = OtherError
        | errno == eBUSY           = ResourceBusy
        | errno == eCHILD          = NoSuchThing
        | errno == eCOMM           = ResourceVanished
        | errno == eCONNABORTED    = OtherError
        | errno == eCONNREFUSED    = NoSuchThing
        | errno == eCONNRESET      = ResourceVanished
        | errno == eDEADLK         = ResourceBusy
        | errno == eDESTADDRREQ    = InvalidArgument
        | errno == eDIRTY          = UnsatisfiedConstraints
        | errno == eDOM            = InvalidArgument
        | errno == eDQUOT          = PermissionDenied
        | errno == eEXIST          = AlreadyExists
        | errno == eFAULT          = OtherError
        | errno == eFBIG           = PermissionDenied
        | errno == eFTYPE          = InappropriateType
        | errno == eHOSTDOWN       = NoSuchThing
        | errno == eHOSTUNREACH    = NoSuchThing
        | errno == eIDRM           = ResourceVanished
        | errno == eILSEQ          = InvalidArgument
        | errno == eINPROGRESS     = AlreadyExists
        | errno == eINTR           = Interrupted
        | errno == eINVAL          = InvalidArgument
        | errno == eIO             = HardwareFault
        | errno == eISCONN         = AlreadyExists
        | errno == eISDIR          = InappropriateType
        | errno == eLOOP           = InvalidArgument
        | errno == eMFILE          = ResourceExhausted
        | errno == eMLINK          = ResourceExhausted
        | errno == eMSGSIZE        = ResourceExhausted
        | errno == eMULTIHOP       = UnsupportedOperation
        | errno == eNAMETOOLONG    = InvalidArgument
        | errno == eNETDOWN        = ResourceVanished
        | errno == eNETRESET       = ResourceVanished
        | errno == eNETUNREACH     = NoSuchThing
        | errno == eNFILE          = ResourceExhausted
        | errno == eNOBUFS         = ResourceExhausted
        | errno == eNODATA         = NoSuchThing
        | errno == eNODEV          = UnsupportedOperation
        | errno == eNOENT          = NoSuchThing
        | errno == eNOEXEC         = InvalidArgument
        | errno == eNOLCK          = ResourceExhausted
        | errno == eNOLINK         = ResourceVanished
        | errno == eNOMEM          = ResourceExhausted
        | errno == eNOMSG          = NoSuchThing
        | errno == eNONET          = NoSuchThing
        | errno == eNOPROTOOPT     = UnsupportedOperation
        | errno == eNOSPC          = ResourceExhausted
        | errno == eNOSR           = ResourceExhausted
        | errno == eNOSTR          = InvalidArgument
        | errno == eNOSYS          = UnsupportedOperation
        | errno == eNOTBLK         = InvalidArgument
        | errno == eNOTCONN        = InvalidArgument
        | errno == eNOTDIR         = InappropriateType
        | errno == eNOTEMPTY       = UnsatisfiedConstraints
        | errno == eNOTSOCK        = InvalidArgument
        | errno == eNOTTY          = IllegalOperation
        | errno == eNXIO           = NoSuchThing
        | errno == eOPNOTSUPP      = UnsupportedOperation
        | errno == ePERM           = PermissionDenied
        | errno == ePFNOSUPPORT    = UnsupportedOperation
        | errno == ePIPE           = ResourceVanished
        | errno == ePROCLIM        = PermissionDenied
        | errno == ePROCUNAVAIL    = UnsupportedOperation
        | errno == ePROGMISMATCH   = ProtocolError
        | errno == ePROGUNAVAIL    = UnsupportedOperation
        | errno == ePROTO          = ProtocolError
        | errno == ePROTONOSUPPORT = ProtocolError
        | errno == ePROTOTYPE      = ProtocolError
        | errno == eRANGE          = UnsupportedOperation
        | errno == eREMCHG         = ResourceVanished
        | errno == eREMOTE         = IllegalOperation
        | errno == eROFS           = PermissionDenied
        | errno == eRPCMISMATCH    = ProtocolError
        | errno == eRREMOTE        = IllegalOperation
        | errno == eSHUTDOWN       = IllegalOperation
        | errno == eSOCKTNOSUPPORT = UnsupportedOperation
        | errno == eSPIPE          = UnsupportedOperation
        | errno == eSRCH           = NoSuchThing
        | errno == eSRMNT          = UnsatisfiedConstraints
        | errno == eSTALE          = ResourceVanished
        | errno == eTIME           = TimeExpired
        | errno == eTIMEDOUT       = TimeExpired
        | errno == eTOOMANYREFS    = ResourceExhausted
        | errno == eTXTBSY         = ResourceBusy
        | errno == eUSERS          = ResourceExhausted
        | errno == eWOULDBLOCK     = OtherError
        | errno == eXDEV           = UnsupportedOperation
        | otherwise                = OtherError

