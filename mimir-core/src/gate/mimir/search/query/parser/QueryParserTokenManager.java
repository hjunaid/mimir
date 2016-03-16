/* Generated By:JavaCC: Do not edit this line. QueryParserTokenManager.java */
package gate.mimir.search.query.parser;
import gate.Annotation;
import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.LanguageAnalyser;
import gate.creole.ANNIEConstants;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.util.OffsetComparator;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.SequenceQuery.Gap;
import gate.mimir.ConstraintType;
import gate.mimir.Constraint;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueryParserTokenManager implements QueryParserConstants
{
  public  java.io.PrintStream debugStream = System.out;
  public  void setDebugStream(java.io.PrintStream ds) { debugStream = ds; }
private final int jjStopStringLiteralDfa_0(int pos, long active0)
{
   switch (pos)
   {
      case 0:
         if ((active0 & 0x40L) != 0L)
            return 15;
         if ((active0 & 0x10000000000L) != 0L)
         {
            jjmatchedKind = 44;
            return 2;
         }
         if ((active0 & 0x84800000000L) != 0L)
         {
            jjmatchedKind = 44;
            return 46;
         }
         return -1;
      case 1:
         if ((active0 & 0x4000000000L) != 0L)
            return 46;
         if ((active0 & 0x90800000000L) != 0L)
         {
            jjmatchedKind = 44;
            jjmatchedPos = 1;
            return 46;
         }
         return -1;
      case 2:
         if ((active0 & 0x90800000000L) != 0L)
         {
            jjmatchedKind = 44;
            jjmatchedPos = 2;
            return 46;
         }
         return -1;
      case 3:
         if ((active0 & 0x10000000000L) != 0L)
            return 46;
         if ((active0 & 0x80800000000L) != 0L)
         {
            jjmatchedKind = 44;
            jjmatchedPos = 3;
            return 46;
         }
         return -1;
      default :
         return -1;
   }
}
private final int jjStartNfa_0(int pos, long active0)
{
   return jjMoveNfa_0(jjStopStringLiteralDfa_0(pos, active0), pos + 1);
}
private final int jjStopAtPos(int pos, int kind)
{
   jjmatchedKind = kind;
   jjmatchedPos = pos;
   return pos + 1;
}
private final int jjStartNfaWithStates_0(int pos, int kind, int state)
{
   jjmatchedKind = kind;
   jjmatchedPos = pos;
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) { return pos + 1; }
   return jjMoveNfa_0(state, pos + 1);
}
private final int jjMoveStringLiteralDfa0_0()
{
   switch(curChar)
   {
      case 13:
         jjmatchedKind = 2;
         return jjMoveStringLiteralDfa1_0(0x8L);
      case 34:
         return jjStartNfaWithStates_0(0, 6, 15);
      case 40:
         return jjStopAtPos(0, 27);
      case 41:
         return jjStopAtPos(0, 28);
      case 43:
         return jjStopAtPos(0, 36);
      case 44:
         return jjStopAtPos(0, 32);
      case 45:
         return jjStopAtPos(0, 39);
      case 46:
         return jjStopAtPos(0, 29);
      case 58:
         return jjStopAtPos(0, 31);
      case 60:
         jjmatchedKind = 23;
         return jjMoveStringLiteralDfa1_0(0x200000L);
      case 61:
         return jjStopAtPos(0, 30);
      case 62:
         jjmatchedKind = 24;
         return jjMoveStringLiteralDfa1_0(0x400000L);
      case 63:
         return jjStopAtPos(0, 37);
      case 73:
         return jjMoveStringLiteralDfa1_0(0x4000000000L);
      case 77:
         return jjMoveStringLiteralDfa1_0(0x800000000L);
      case 79:
         return jjMoveStringLiteralDfa1_0(0x10000000000L);
      case 82:
         return jjMoveStringLiteralDfa1_0(0x80000000000L);
      case 91:
         return jjStopAtPos(0, 41);
      case 93:
         return jjStopAtPos(0, 42);
      case 123:
         return jjStopAtPos(0, 25);
      case 125:
         return jjStopAtPos(0, 26);
      default :
         return jjMoveNfa_0(1, 0);
   }
}
private final int jjMoveStringLiteralDfa1_0(long active0)
{
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(0, active0);
      return 1;
   }
   switch(curChar)
   {
      case 10:
         if ((active0 & 0x8L) != 0L)
            return jjStopAtPos(1, 3);
         break;
      case 61:
         if ((active0 & 0x200000L) != 0L)
            return jjStopAtPos(1, 21);
         else if ((active0 & 0x400000L) != 0L)
            return jjStopAtPos(1, 22);
         break;
      case 69:
         return jjMoveStringLiteralDfa2_0(active0, 0x80000000000L);
      case 73:
         return jjMoveStringLiteralDfa2_0(active0, 0x800000000L);
      case 78:
         if ((active0 & 0x4000000000L) != 0L)
            return jjStartNfaWithStates_0(1, 38, 46);
         break;
      case 86:
         return jjMoveStringLiteralDfa2_0(active0, 0x10000000000L);
      default :
         break;
   }
   return jjStartNfa_0(0, active0);
}
private final int jjMoveStringLiteralDfa2_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(0, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(1, active0);
      return 2;
   }
   switch(curChar)
   {
      case 69:
         return jjMoveStringLiteralDfa3_0(active0, 0x10000000000L);
      case 71:
         return jjMoveStringLiteralDfa3_0(active0, 0x80000000000L);
      case 78:
         return jjMoveStringLiteralDfa3_0(active0, 0x800000000L);
      default :
         break;
   }
   return jjStartNfa_0(1, active0);
}
private final int jjMoveStringLiteralDfa3_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(1, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(2, active0);
      return 3;
   }
   switch(curChar)
   {
      case 69:
         return jjMoveStringLiteralDfa4_0(active0, 0x80000000000L);
      case 82:
         if ((active0 & 0x10000000000L) != 0L)
            return jjStartNfaWithStates_0(3, 40, 46);
         break;
      case 85:
         return jjMoveStringLiteralDfa4_0(active0, 0x800000000L);
      default :
         break;
   }
   return jjStartNfa_0(2, active0);
}
private final int jjMoveStringLiteralDfa4_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(2, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(3, active0);
      return 4;
   }
   switch(curChar)
   {
      case 83:
         if ((active0 & 0x800000000L) != 0L)
            return jjStartNfaWithStates_0(4, 35, 46);
         break;
      case 88:
         if ((active0 & 0x80000000000L) != 0L)
            return jjStartNfaWithStates_0(4, 43, 46);
         break;
      default :
         break;
   }
   return jjStartNfa_0(3, active0);
}
private final void jjCheckNAdd(int state)
{
   if (jjrounds[state] != jjround)
   {
      jjstateSet[jjnewStateCnt++] = state;
      jjrounds[state] = jjround;
   }
}
private final void jjAddStates(int start, int end)
{
   do {
      jjstateSet[jjnewStateCnt++] = jjnextStates[start];
   } while (start++ != end);
}
private final void jjCheckNAddTwoStates(int state1, int state2)
{
   jjCheckNAdd(state1);
   jjCheckNAdd(state2);
}
private final void jjCheckNAddStates(int start, int end)
{
   do {
      jjCheckNAdd(jjnextStates[start]);
   } while (start++ != end);
}
private final void jjCheckNAddStates(int start)
{
   jjCheckNAdd(jjnextStates[start]);
   jjCheckNAdd(jjnextStates[start + 1]);
}
static final long[] jjbitVec0 = {
   0x0L, 0x0L, 0x100c00000000L, 0x0L
};
static final long[] jjbitVec1 = {
   0x0L, 0x0L, 0x100000000000L, 0x0L
};
static final long[] jjbitVec2 = {
   0x1ff00000fffffffeL, 0xffffffffffffc000L, 0xffffffffL, 0x600000000000000L
};
static final long[] jjbitVec4 = {
   0x0L, 0x0L, 0x0L, 0xff7fffffff7fffffL
};
static final long[] jjbitVec5 = {
   0x0L, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL
};
static final long[] jjbitVec6 = {
   0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffL, 0x0L
};
static final long[] jjbitVec7 = {
   0xffffffffffffffffL, 0xffffffffffffffffL, 0x0L, 0x0L
};
static final long[] jjbitVec8 = {
   0x3fffffffffffL, 0x0L, 0x0L, 0x0L
};
private final int jjMoveNfa_0(int startState, int curPos)
{
   int[] nextStates;
   int startsAt = 0;
   jjnewStateCnt = 47;
   int i = 1;
   jjstateSet[0] = startState;
   int j, kind = 0x7fffffff;
   for (;;)
   {
      if (++jjround == 0x7fffffff)
         ReInitRounds();
      if (curChar < 64)
      {
         long l = 1L << curChar;
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 1:
                  if ((0x3ff000000000000L & l) != 0L)
                  {
                     if (kind > 18)
                        kind = 18;
                     jjCheckNAdd(0);
                  }
                  else if ((0x4ba00000000L & l) != 0L)
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAddStates(0, 2);
                  }
                  else if (curChar == 34)
                     jjAddStates(3, 7);
                  else if (curChar == 38)
                  {
                     if (kind > 34)
                        kind = 34;
                  }
                  if (curChar == 36)
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAdd(46);
                  }
                  break;
               case 2:
               case 46:
                  if ((0x3ff001000000000L & l) == 0L)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAdd(46);
                  break;
               case 0:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 18)
                     kind = 18;
                  jjCheckNAdd(0);
                  break;
               case 4:
                  if (curChar == 38 && kind > 34)
                     kind = 34;
                  break;
               case 8:
                  if ((0x4ba00000000L & l) == 0L)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 11:
                  if (curChar == 34)
                     jjAddStates(3, 7);
                  break;
               case 12:
                  if (curChar != 34)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 30:
                  if (curChar != 40)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 31:
                  if (curChar != 41)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 32:
                  if (curChar != 58)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 33:
                  if (curChar != 43)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 34:
                  if (curChar != 38)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 36:
                  if (curChar != 63)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 38:
                  if (curChar != 46)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 39:
                  if (curChar != 61)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 42:
                  if (curChar != 60)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 43:
                  if (curChar != 62)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 44:
                  if (curChar != 44)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 45:
                  if (curChar != 36)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAdd(46);
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      else if (curChar < 128)
      {
         long l = 1L << (curChar & 077);
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 1:
                  if ((0x7fffffe87fffffeL & l) != 0L)
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAdd(46);
                  }
                  else if (curChar == 92)
                     jjCheckNAddStates(8, 25);
                  else if (curChar == 124)
                  {
                     if (kind > 33)
                        kind = 33;
                  }
                  if ((0x40000001c0000001L & l) != 0L)
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAddStates(0, 2);
                  }
                  else if (curChar == 65)
                     jjstateSet[jjnewStateCnt++] = 6;
                  else if (curChar == 79)
                     jjstateSet[jjnewStateCnt++] = 2;
                  break;
               case 15:
                  if (curChar == 82)
                     jjstateSet[jjnewStateCnt++] = 27;
                  else if (curChar == 79)
                     jjstateSet[jjnewStateCnt++] = 22;
                  else if (curChar == 73)
                     jjstateSet[jjnewStateCnt++] = 18;
                  else if (curChar == 65)
                     jjstateSet[jjnewStateCnt++] = 14;
                  if (curChar == 79)
                     jjstateSet[jjnewStateCnt++] = 16;
                  break;
               case 2:
                  if ((0x7fffffe87fffffeL & l) != 0L)
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAdd(46);
                  }
                  if (curChar == 82)
                  {
                     if (kind > 33)
                        kind = 33;
                  }
                  break;
               case 3:
                  if (curChar == 79)
                     jjstateSet[jjnewStateCnt++] = 2;
                  break;
               case 5:
                  if (curChar == 68 && kind > 34)
                     kind = 34;
                  break;
               case 6:
                  if (curChar == 78)
                     jjstateSet[jjnewStateCnt++] = 5;
                  break;
               case 7:
                  if (curChar == 65)
                     jjstateSet[jjnewStateCnt++] = 6;
                  break;
               case 8:
                  if ((0x40000001c0000001L & l) == 0L)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 9:
                  if (curChar == 92)
                     jjCheckNAddStates(8, 25);
                  break;
               case 10:
                  if (curChar != 91)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 13:
                  if (curChar == 68)
                     jjCheckNAdd(12);
                  break;
               case 14:
                  if (curChar == 78)
                     jjstateSet[jjnewStateCnt++] = 13;
                  break;
               case 16:
               case 20:
                  if (curChar == 82)
                     jjCheckNAdd(12);
                  break;
               case 17:
                  if (curChar == 79)
                     jjstateSet[jjnewStateCnt++] = 16;
                  break;
               case 18:
                  if (curChar == 78)
                     jjCheckNAdd(12);
                  break;
               case 19:
                  if (curChar == 73)
                     jjstateSet[jjnewStateCnt++] = 18;
                  break;
               case 21:
                  if (curChar == 69)
                     jjstateSet[jjnewStateCnt++] = 20;
                  break;
               case 22:
                  if (curChar == 86)
                     jjstateSet[jjnewStateCnt++] = 21;
                  break;
               case 23:
                  if (curChar == 79)
                     jjstateSet[jjnewStateCnt++] = 22;
                  break;
               case 24:
                  if (curChar == 88)
                     jjCheckNAdd(12);
                  break;
               case 25:
                  if (curChar == 69)
                     jjstateSet[jjnewStateCnt++] = 24;
                  break;
               case 26:
                  if (curChar == 71)
                     jjstateSet[jjnewStateCnt++] = 25;
                  break;
               case 27:
                  if (curChar == 69)
                     jjstateSet[jjnewStateCnt++] = 26;
                  break;
               case 28:
                  if (curChar == 82)
                     jjstateSet[jjnewStateCnt++] = 27;
                  break;
               case 29:
                  if (curChar != 93)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 35:
                  if (curChar != 124)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 37:
                  if (curChar != 92)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 40:
                  if (curChar != 123)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 41:
                  if (curChar != 125)
                     break;
                  kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 45:
                  if ((0x7fffffe87fffffeL & l) == 0L)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAdd(46);
                  break;
               case 46:
                  if ((0x7fffffe87fffffeL & l) == 0L)
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAdd(46);
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      else
      {
         int hiByte = (int)(curChar >> 8);
         int i1 = hiByte >> 6;
         long l1 = 1L << (hiByte & 077);
         int i2 = (curChar & 0xff) >> 6;
         long l2 = 1L << (curChar & 077);
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 1:
                  if (jjCanMove_0(hiByte, i1, i2, l1, l2))
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAddStates(0, 2);
                  }
                  if (jjCanMove_1(hiByte, i1, i2, l1, l2))
                  {
                     if (kind > 44)
                        kind = 44;
                     jjCheckNAdd(46);
                  }
                  break;
               case 2:
               case 46:
                  if (!jjCanMove_1(hiByte, i1, i2, l1, l2))
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAdd(46);
                  break;
               case 8:
                  if (!jjCanMove_0(hiByte, i1, i2, l1, l2))
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAddStates(0, 2);
                  break;
               case 45:
                  if (!jjCanMove_1(hiByte, i1, i2, l1, l2))
                     break;
                  if (kind > 44)
                     kind = 44;
                  jjCheckNAdd(46);
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      if (kind != 0x7fffffff)
      {
         jjmatchedKind = kind;
         jjmatchedPos = curPos;
         kind = 0x7fffffff;
      }
      ++curPos;
      if ((i = jjnewStateCnt) == (startsAt = 47 - (jjnewStateCnt = startsAt)))
         return curPos;
      try { curChar = input_stream.readChar(); }
      catch(java.io.IOException e) { return curPos; }
   }
}
private final int jjStopStringLiteralDfa_1(int pos, long active0)
{
   switch (pos)
   {
      case 0:
         if ((active0 & 0x7f80L) != 0L)
            return 0;
         return -1;
      default :
         return -1;
   }
}
private final int jjStartNfa_1(int pos, long active0)
{
   return jjMoveNfa_1(jjStopStringLiteralDfa_1(pos, active0), pos + 1);
}
private final int jjStartNfaWithStates_1(int pos, int kind, int state)
{
   jjmatchedKind = kind;
   jjmatchedPos = pos;
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) { return pos + 1; }
   return jjMoveNfa_1(state, pos + 1);
}
private final int jjMoveStringLiteralDfa0_1()
{
   switch(curChar)
   {
      case 34:
         return jjStopAtPos(0, 17);
      case 92:
         return jjMoveStringLiteralDfa1_1(0x7f80L);
      default :
         return jjMoveNfa_1(5, 0);
   }
}
private final int jjMoveStringLiteralDfa1_1(long active0)
{
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_1(0, active0);
      return 1;
   }
   switch(curChar)
   {
      case 34:
         if ((active0 & 0x1000L) != 0L)
            return jjStopAtPos(1, 12);
         break;
      case 39:
         if ((active0 & 0x2000L) != 0L)
            return jjStopAtPos(1, 13);
         break;
      case 92:
         if ((active0 & 0x4000L) != 0L)
            return jjStopAtPos(1, 14);
         break;
      case 98:
         if ((active0 & 0x400L) != 0L)
            return jjStopAtPos(1, 10);
         break;
      case 102:
         if ((active0 & 0x800L) != 0L)
            return jjStopAtPos(1, 11);
         break;
      case 110:
         if ((active0 & 0x80L) != 0L)
            return jjStopAtPos(1, 7);
         break;
      case 114:
         if ((active0 & 0x100L) != 0L)
            return jjStopAtPos(1, 8);
         break;
      case 116:
         if ((active0 & 0x200L) != 0L)
            return jjStopAtPos(1, 9);
         break;
      default :
         break;
   }
   return jjStartNfa_1(0, active0);
}
static final long[] jjbitVec9 = {
   0xfffffffffffffffeL, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL
};
static final long[] jjbitVec10 = {
   0x0L, 0x0L, 0xffffffffffffffffL, 0xffffffffffffffffL
};
private final int jjMoveNfa_1(int startState, int curPos)
{
   int[] nextStates;
   int startsAt = 0;
   jjnewStateCnt = 7;
   int i = 1;
   jjstateSet[0] = startState;
   int j, kind = 0x7fffffff;
   for (;;)
   {
      if (++jjround == 0x7fffffff)
         ReInitRounds();
      if (curChar < 64)
      {
         long l = 1L << curChar;
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 5:
                  if ((0xfffffffbffffffffL & l) != 0L && kind > 16)
                     kind = 16;
                  break;
               case 1:
                  if ((0x3ff000000000000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 2;
                  break;
               case 2:
                  if ((0x3ff000000000000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 3;
                  break;
               case 3:
                  if ((0x3ff000000000000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 4;
                  break;
               case 4:
                  if ((0x3ff000000000000L & l) != 0L && kind > 15)
                     kind = 15;
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      else if (curChar < 128)
      {
         long l = 1L << (curChar & 077);
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 5:
                  if ((0xffffffffefffffffL & l) != 0L)
                  {
                     if (kind > 16)
                        kind = 16;
                  }
                  else if (curChar == 92)
                     jjstateSet[jjnewStateCnt++] = 0;
                  break;
               case 0:
                  if (curChar == 117)
                     jjstateSet[jjnewStateCnt++] = 1;
                  break;
               case 1:
                  if ((0x7e0000007eL & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 2;
                  break;
               case 2:
                  if ((0x7e0000007eL & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 3;
                  break;
               case 3:
                  if ((0x7e0000007eL & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 4;
                  break;
               case 4:
                  if ((0x7e0000007eL & l) != 0L && kind > 15)
                     kind = 15;
                  break;
               case 6:
                  if ((0xffffffffefffffffL & l) != 0L && kind > 16)
                     kind = 16;
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      else
      {
         int hiByte = (int)(curChar >> 8);
         int i1 = hiByte >> 6;
         long l1 = 1L << (hiByte & 077);
         int i2 = (curChar & 0xff) >> 6;
         long l2 = 1L << (curChar & 077);
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 5:
                  if (jjCanMove_2(hiByte, i1, i2, l1, l2) && kind > 16)
                     kind = 16;
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      if (kind != 0x7fffffff)
      {
         jjmatchedKind = kind;
         jjmatchedPos = curPos;
         kind = 0x7fffffff;
      }
      ++curPos;
      if ((i = jjnewStateCnt) == (startsAt = 7 - (jjnewStateCnt = startsAt)))
         return curPos;
      try { curChar = input_stream.readChar(); }
      catch(java.io.IOException e) { return curPos; }
   }
}
static final int[] jjnextStates = {
   8, 9, 11, 15, 17, 19, 23, 28, 10, 29, 30, 31, 32, 33, 34, 35, 
   36, 37, 38, 12, 39, 40, 41, 42, 43, 44, 
};
private static final boolean jjCanMove_0(int hiByte, int i1, int i2, long l1, long l2)
{
   switch(hiByte)
   {
      case 0:
         return ((jjbitVec0[i2] & l2) != 0L);
      case 32:
         return ((jjbitVec1[i2] & l2) != 0L);
      default : 
         return false;
   }
}
private static final boolean jjCanMove_1(int hiByte, int i1, int i2, long l1, long l2)
{
   switch(hiByte)
   {
      case 0:
         return ((jjbitVec4[i2] & l2) != 0L);
      case 48:
         return ((jjbitVec5[i2] & l2) != 0L);
      case 49:
         return ((jjbitVec6[i2] & l2) != 0L);
      case 51:
         return ((jjbitVec7[i2] & l2) != 0L);
      case 61:
         return ((jjbitVec8[i2] & l2) != 0L);
      default : 
         if ((jjbitVec2[i1] & l1) != 0L)
            return true;
         return false;
   }
}
private static final boolean jjCanMove_2(int hiByte, int i1, int i2, long l1, long l2)
{
   switch(hiByte)
   {
      case 0:
         return ((jjbitVec10[i2] & l2) != 0L);
      default : 
         if ((jjbitVec9[i1] & l1) != 0L)
            return true;
         return false;
   }
}
public static final String[] jjstrLiteralImages = {
"", null, null, null, null, null, null, null, null, null, null, null, null, 
null, null, null, null, null, null, null, null, "\74\75", "\76\75", "\74", "\76", 
"\173", "\175", "\50", "\51", "\56", "\75", "\72", "\54", null, null, 
"\115\111\116\125\123", "\53", "\77", "\111\116", "\55", "\117\126\105\122", "\133", "\135", 
"\122\105\107\105\130", null, null, null, };
public static final String[] lexStateNames = {
   "DEFAULT", 
   "IN_STRING", 
};
public static final int[] jjnewLexState = {
   -1, -1, -1, -1, -1, -1, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1, -1, 
   -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 
};
static final long[] jjtoToken = {
   0x1fffffe60001L, 
};
static final long[] jjtoSkip = {
   0x3eL, 
};
static final long[] jjtoMore = {
   0x1ffc0L, 
};
protected SimpleCharStream input_stream;
private final int[] jjrounds = new int[47];
private final int[] jjstateSet = new int[94];
StringBuffer image;
int jjimageLen;
int lengthOfMatch;
protected char curChar;
public QueryParserTokenManager(SimpleCharStream stream){
   if (SimpleCharStream.staticFlag)
      throw new Error("ERROR: Cannot use a static CharStream class with a non-static lexical analyzer.");
   input_stream = stream;
}
public QueryParserTokenManager(SimpleCharStream stream, int lexState){
   this(stream);
   SwitchTo(lexState);
}
public void ReInit(SimpleCharStream stream)
{
   jjmatchedPos = jjnewStateCnt = 0;
   curLexState = defaultLexState;
   input_stream = stream;
   ReInitRounds();
}
private final void ReInitRounds()
{
   int i;
   jjround = 0x80000001;
   for (i = 47; i-- > 0;)
      jjrounds[i] = 0x80000000;
}
public void ReInit(SimpleCharStream stream, int lexState)
{
   ReInit(stream);
   SwitchTo(lexState);
}
public void SwitchTo(int lexState)
{
   if (lexState >= 2 || lexState < 0)
      throw new TokenMgrError("Error: Ignoring invalid lexical state : " + lexState + ". State unchanged.", TokenMgrError.INVALID_LEXICAL_STATE);
   else
      curLexState = lexState;
}

protected Token jjFillToken()
{
   Token t = Token.newToken(jjmatchedKind);
   t.kind = jjmatchedKind;
   String im = jjstrLiteralImages[jjmatchedKind];
   t.image = (im == null) ? input_stream.GetImage() : im;
   t.beginLine = input_stream.getBeginLine();
   t.beginColumn = input_stream.getBeginColumn();
   t.endLine = input_stream.getEndLine();
   t.endColumn = input_stream.getEndColumn();
   return t;
}

int curLexState = 0;
int defaultLexState = 0;
int jjnewStateCnt;
int jjround;
int jjmatchedPos;
int jjmatchedKind;

public Token getNextToken() 
{
  int kind;
  Token specialToken = null;
  Token matchedToken;
  int curPos = 0;

  EOFLoop :
  for (;;)
  {   
   try   
   {     
      curChar = input_stream.BeginToken();
   }     
   catch(java.io.IOException e)
   {        
      jjmatchedKind = 0;
      matchedToken = jjFillToken();
      return matchedToken;
   }
   image = null;
   jjimageLen = 0;

   for (;;)
   {
     switch(curLexState)
     {
       case 0:
         try { input_stream.backup(0);
            while (curChar <= 32 && (0x100000600L & (1L << curChar)) != 0L)
               curChar = input_stream.BeginToken();
         }
         catch (java.io.IOException e1) { continue EOFLoop; }
         jjmatchedKind = 0x7fffffff;
         jjmatchedPos = 0;
         curPos = jjMoveStringLiteralDfa0_0();
         break;
       case 1:
         jjmatchedKind = 0x7fffffff;
         jjmatchedPos = 0;
         curPos = jjMoveStringLiteralDfa0_1();
         break;
     }
     if (jjmatchedKind != 0x7fffffff)
     {
        if (jjmatchedPos + 1 < curPos)
           input_stream.backup(curPos - jjmatchedPos - 1);
        if ((jjtoToken[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)
        {
           matchedToken = jjFillToken();
           TokenLexicalActions(matchedToken);
       if (jjnewLexState[jjmatchedKind] != -1)
         curLexState = jjnewLexState[jjmatchedKind];
           return matchedToken;
        }
        else if ((jjtoSkip[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)
        {
         if (jjnewLexState[jjmatchedKind] != -1)
           curLexState = jjnewLexState[jjmatchedKind];
           continue EOFLoop;
        }
        MoreLexicalActions();
      if (jjnewLexState[jjmatchedKind] != -1)
        curLexState = jjnewLexState[jjmatchedKind];
        curPos = 0;
        jjmatchedKind = 0x7fffffff;
        try {
           curChar = input_stream.readChar();
           continue;
        }
        catch (java.io.IOException e1) { }
     }
     int error_line = input_stream.getEndLine();
     int error_column = input_stream.getEndColumn();
     String error_after = null;
     boolean EOFSeen = false;
     try { input_stream.readChar(); input_stream.backup(1); }
     catch (java.io.IOException e1) {
        EOFSeen = true;
        error_after = curPos <= 1 ? "" : input_stream.GetImage();
        if (curChar == '\n' || curChar == '\r') {
           error_line++;
           error_column = 0;
        }
        else
           error_column++;
     }
     if (!EOFSeen) {
        input_stream.backup(1);
        error_after = curPos <= 1 ? "" : input_stream.GetImage();
     }
     throw new TokenMgrError(EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenMgrError.LEXICAL_ERROR);
   }
  }
}

void MoreLexicalActions()
{
   jjimageLen += (lengthOfMatch = jjmatchedPos + 1);
   switch(jjmatchedKind)
   {
      case 7 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
             image.setLength(image.length() - 2); image.append("\n");
         break;
      case 8 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
             image.setLength(image.length() - 2); image.append("\r");
         break;
      case 9 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
             image.setLength(image.length() - 2); image.append("\t");
         break;
      case 10 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
             image.setLength(image.length() - 2); image.append("\b");
         break;
      case 11 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
             image.setLength(image.length() - 2); image.append("\f");
         break;
      case 12 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
              image.setLength(image.length() - 2); image.append("\"");
         break;
      case 13 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
              image.setLength(image.length() - 2); image.append("\'");
         break;
      case 14 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
              image.setLength(image.length() - 2); image.append("\\");
         break;
      case 15 :
         if (image == null)
            image = new StringBuffer();
         image.append(input_stream.GetSuffix(jjimageLen));
         jjimageLen = 0;
             String digits = image.substring(image.length() - 4, image.length());
             image.setLength(image.length() - 6);
             image.append((char)Integer.parseInt(digits, 16));
         break;
      default : 
         break;
   }
}
void TokenLexicalActions(Token matchedToken)
{
   switch(jjmatchedKind)
   {
      case 17 :
        if (image == null)
            image = new StringBuffer();
            image.append(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));
    // image.setLength(image.length() - 1);
     matchedToken.image = image.toString();
         break;
      default : 
         break;
   }
}
}
