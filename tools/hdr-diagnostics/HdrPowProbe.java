import java.util.*;
public class HdrPowProbe {
 public static void main(String[] args){ Scanner in=new Scanner(System.in); while(in.hasNextLong()){float g=Float.intBitsToFloat((int)in.nextLong());int count=in.nextInt();float e=1f/g;for(int i=0;i<count;i++){float v=Float.intBitsToFloat((int)in.nextLong());System.out.print(Integer.toUnsignedString(Float.floatToRawIntBits((float)Math.pow(v,e)))+" ");}System.out.println();}}
}
