/*
 * Copyright (c) 2007-2012 Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * This file is part of MSPSim.
 *
 * -----------------------------------------------------------------
 *
 */
package fwpx.mspsim;

import se.sics.mspsim.core.*;
import se.sics.mspsim.core.EmulationLogger.WarningType;
import se.sics.mspsim.util.ArrayFIFO;
import se.sics.mspsim.util.CCITT_CRC;
import se.sics.mspsim.util.Utils;

import java.util.*;

public class Transceiver extends Chip implements USARTListener, PacketListener, PacketSource {

  public enum Reg {
    SNOP, SXOSCON, STXCAL, SRXON, /* 0x00 */
    STXON, STXONCCA, SRFOFF, SXOSCOFF, /* 0x04 */
    SFLUSHRX, SFLUSHTX, SACK, SACKPEND, /* 0x08 */
    SRXDEC, STXENC, SAES, foo,   /* 0x0c */
    MAIN, MDMCTRL0, MDMCTRL1, RSSI, /* 0x10 */
    SYNCWORD, TXCTRL, RXCTRL0, RXCTRL1, /* 0x14 */
    FSCTRL, SECCTRL0, SECCTRL1, BATTMON, /* 0x18 */
    IOCFG0, IOCFG1, MANFIDL, MANFIDH, /* 0x1c */
    FSMTC, MANAND, MANOR, AGCCTRL, /* 0x20 */
    AGCTST0, AGCTST1, AGCTST2, FSTST0, /* 0x24 */
    FSTST1, FSTST2, FSTST3, RXBPFTST, /* 0x28 */
    FSMSTATE, ADCTST, DACTST, TOPTST,
    RESERVED, RES1, RES2, RES3,  /* 0x30 */
    RES4, RES5, RES6, RES7,
    RES8, RES9, RESa, RESb,
    RESc, RESd, TXFIFO, RXFIFO
  }

  public enum SpiState {
    WAITING, WRITE_REGISTER, READ_REGISTER, RAM_ACCESS,
    READ_RXFIFO, WRITE_TXFIFO
  }


  public static final int REG_SNOP		= 0x00;
  public static final int REG_SXOSCON           = 0x01;
  public static final int REG_STXCAL		= 0x02;
  public static final int REG_SRXON		= 0x03;
  public static final int REG_STXON		= 0x04;
  public static final int REG_STXONCCA          = 0x05;
  public static final int REG_SRFOFF		= 0x06;
  public static final int REG_SXOSCOFF          = 0x07;
  public static final int REG_SFLUSHRX          = 0x08;
  public static final int REG_SFLUSHTX          = 0x09;
  public static final int REG_SACK		= 0x0A;
  public static final int REG_SACKPEND          = 0x0B;
  public static final int REG_SRXDEC		= 0x0C;
  public static final int REG_STXENC		= 0x0D;
  public static final int REG_SAES		= 0x0E;
  public static final int REG_foo		= 0x0F;
  public static final int REG_MAIN		= 0x10;
  public static final int REG_MDMCTRL0          = 0x11;
  public static final int REG_MDMCTRL1          = 0x12;
  public static final int REG_RSSI		= 0x13;
  public static final int REG_SYNCWORD          = 0x14;
  public static final int REG_TXCTRL		= 0x15;
  public static final int REG_RXCTRL0           = 0x16;
  public static final int REG_RXCTRL1           = 0x17;
  public static final int REG_FSCTRL		= 0x18;
  public static final int REG_SECCTRL0          = 0x19;
  public static final int REG_SECCTRL1          = 0x1A;
  public static final int REG_BATTMON           = 0x1B;
  public static final int REG_IOCFG0		= 0x1C;
  public static final int REG_IOCFG1		= 0x1D;
  public static final int REG_MANFIDL           = 0x1E;
  public static final int REG_MANFIDH           = 0x1F;
  public static final int REG_FSMTC		= 0x20;
  public static final int REG_MANAND		= 0x21;
  public static final int REG_MANOR		= 0x22;
  public static final int REG_AGCCTRL           = 0x23;
  public static final int REG_AGCTST0           = 0x24;
  public static final int REG_AGCTST1           = 0x25;
  public static final int REG_AGCTST2           = 0x26;
  public static final int REG_FSTST0		= 0x27;
  public static final int REG_FSTST1		= 0x28;
  public static final int REG_FSTST2		= 0x29;
  public static final int REG_FSTST3		= 0x2A;
  public static final int REG_RXBPFTST          = 0x2B;
  public static final int REG_FSMSTATE          = 0x2C;
  public static final int REG_ADCTST		= 0x2D;
  public static final int REG_DACTST		= 0x2E;
  public static final int REG_TOPTST		= 0x2F;
  public static final int REG_RESERVED          = 0x30;
  /* 0x31 - 0x3D not used */
  public static final int REG_TXFIFO		= 0x3E;
  public static final int REG_RXFIFO		= 0x3F;

  public static final int STATUS_XOSC16M_STABLE = 1 << 6;
  public static final int STATUS_TX_UNDERFLOW   = 1 << 5;
  public static final int STATUS_ENC_BUSY	    = 1 << 4;
  public static final int STATUS_TX_ACTIVE	= 1 << 3;
  public static final int STATUS_LOCK	= 1 << 2;
  public static final int STATUS_RSSI_VALID	= 1 << 1;

  // IOCFG0 Register Bit masks
  public static final int BCN_ACCEPT = (1<<11);
  public static final int FIFO_POLARITY = (1<<10);
  public static final int FIFOP_POLARITY = (1<<9);
  public static final int SFD_POLARITY = (1<<8);
  public static final int CCA_POLARITY = (1<<7);
  public static final int POLARITY_MASK = FIFO_POLARITY | FIFOP_POLARITY | SFD_POLARITY | CCA_POLARITY;
  public static final int FIFOP_THR = 0x7F;

  // IOCFG1 Register Bit Masks
  public static final int SFDMUX = 0x3E0;
  public static final int CCAMUX = 0x1F;

  public static final int SFDMUX_SHIFT = 5;
  public static final int CCAMUX_SHIFT = 0;

  // CCAMUX values
  public static final int CCAMUX_CCA = 0;
  public static final int CCAMUX_XOSC16M_STABLE = 24;

  // MDMCTRO0 values
  public static final int ADR_DECODE = (1 << 11);
  public static final int ADR_AUTOCRC = (1 << 5);
  public static final int AUTOACK = (1 << 4);
  public static final int PREAMBLE_LENGTH = 0x0f;

  // RAM Addresses
  public static final int RAM_TXFIFO	= 0x000;
  public static final int RAM_RXFIFO	= 0x080;
  public static final int RAM_KEY0	= 0x100;
  public static final int RAM_RXNONCE	= 0x110;
  public static final int RAM_SABUF	= 0x120;
  public static final int RAM_KEY1	= 0x130;
  public static final int RAM_TXNONCE	= 0x140;
  public static final int RAM_CBCSTATE	= 0x150;
  public static final int RAM_IEEEADDR	= 0x160;
  public static final int RAM_PANID	= 0x168;
  public static final int RAM_SHORTADDR	= 0x16A;

  public static final int SHORT_ADDRESS = 2;
  public static final int LONG_ADDRESS = 3;

  public static final int TXFIFO_SZ = RAM_RXFIFO - RAM_TXFIFO;


  // The Operation modes of the CC2420
  public static final int MODE_TXRX_OFF = 0x00;
  public static final int MODE_RX_ON = 0x01;
  public static final int MODE_TXRX_ON = 0x02;
  public static final int MODE_POWER_OFF = 0x03;
  public static final int MODE_MAX = MODE_POWER_OFF;
  private static final String[] MODE_NAMES = {
      "off", "listen", "transmit", "power_off"
  };

  // State Machine - Datasheet Figure 25 page 44
  public enum RadioState {
    VREG_OFF(-1),
    POWER_DOWN(0),
    IDLE(1),
    RX_CALIBRATE(2),
    RX_SFD_SEARCH(3),
    RX_WAIT(14),
    RX_FRAME(16),
    RX_OVERFLOW(17);

    private final int state;
    RadioState(int stateNo) {
      state = stateNo;
    }

    public int getFSMState() {
      return state;
    }
  }

  // FCF High
  public static final int FRAME_TYPE = 0x07;
  public static final int SECURITY_ENABLED = (1<<3);
  public static final int FRAME_PENDING = (1<<4);
  public static final int ACK_REQUEST = (1<<5);
  public static final int INTRA_PAN = (1<<6);

  public static final int TYPE_BEACON_FRAME = 0x00;
  public static final int TYPE_DATA_FRAME = 0x01;
  public static final int TYPE_ACK_FRAME = 0x02;
  public static final int TYPE_CMD_FRAME = 0x03;

  // FCF Low
  public static final int DESTINATION_ADDRESS_MODE = 0x30;
  public static final int SOURCE_ADDRESS_MODE = 0x3;

  // Position of SEQ-NO in ACK packet...
  public static final int ACK_SEQPOS = 3;

  private RadioState stateMachine = RadioState.VREG_OFF;

  // 802.15.4 symbol period in ms
  public static final double SYMBOL_PERIOD = 0.016; // 16 us

  // when reading registers this flag is set!
  public static final int FLAG_READ = 0x40;

  public static final int FLAG_RAM = 0x80;
  // When accessing RAM the second byte of the address contains
  // a flag indicating read/write
  public static final int FLAG_RAM_READ = 0x20;
  private static final int[] BC_ADDRESS = {0xff, 0xff};

  private SpiState state = SpiState.WAITING;
  private int usartDataPos;
  private int usartDataAddress;
  private int usartDataValue;
  private int shrPos;
  private int txfifoPos;
  private boolean txfifoFlush;	// TXFIFO is automatically flushed on next write
  private int rxlen;
  private int rxread;
  private int zeroSymbols;
  private boolean ramRead;

  /* RSSI is an externally set value of the RSSI for this CC2420 */
  /* low RSSI => CCA = true in normal mode */

  private int rssi = -100;
  private static final int RSSI_OFFSET = -45; /* cc2420 datasheet */

  /* This is the magical LQI */
  private int corrval = 37;

  /* FIFOP Threshold */
  private int fifopThr = 64;

  /* if autoack is configured or if */
  private boolean autoAck;
  private boolean shouldAck;
  private boolean addressDecode;
  private boolean ackRequest;
  private boolean autoCRC;

  // Data from last received packet
  private int dsn;
  private int fcf0;
  private int fcf1;
  private int frameType;
  private boolean crcOk;

  private int activeFrequency;
  private int activeChannel;

  //private int status = STATUS_XOSC16M_STABLE | STATUS_RSSI_VALID;
  private int status;

  private final int[] registers = new int[64];
  // More than needed...
  private final int[] memory = new int[512];

  // Buffer to hold 5 byte Synchronization header, as it is not written to the TXFIFO
  private final byte[] SHR = new byte[5];

  private boolean chipSelect;

  private IOPort ccaPort;
  private int ccaPin;

  private IOPort fifopPort;
  private int fifopPin;

  private IOPort fifoPort;
  private int fifoPin;

  private IOPort sfdPort;
  private int sfdPin;

  private int txCursor;
  private boolean on;

  private final TimeEvent oscillatorEvent = new TimeEvent(0, "CC2420 OSC") {
    @Override
    public void execute(long t) {
      status |= STATUS_XOSC16M_STABLE;
      if (logLevel > INFO) log("Oscillator Stable Event.");
      setState(RadioState.IDLE);
      if( (registers[REG_IOCFG1] & CCAMUX) == CCAMUX_XOSC16M_STABLE) {
      } else {
        if(logLevel > INFO) log("CCAMUX != CCA_XOSC16M_STABLE! Not raising CCA");
      }
    }
  };

  private final TimeEvent vregEvent = new TimeEvent(0, "CC2420 VREG") {
    @Override
    public void execute(long t) {
      if(logLevel > INFO) log("VREG Started at: " + t + " cyc: " +
          cpu.cycles + " " + getTime());
      on = true;
      setState(RadioState.POWER_DOWN);
    }
  };

  private final TimeEvent symbolEvent = new TimeEvent(0, "CC2420 Symbol") {
    @Override
    public void execute(long t) {
      switch(stateMachine) {
        case RX_CALIBRATE:
        case RX_WAIT:
          setState(RadioState.RX_SFD_SEARCH);
          break;
        /* this will be called 8 symbols after first SFD_SEARCH */
        case RX_SFD_SEARCH:
          status |= STATUS_RSSI_VALID;
          break;
      }
    }
  };

  private boolean currentCCA;
  private boolean currentSFD;
  private boolean currentFIFO;
  private boolean currentFIFOP;

  public interface StateListener {
    void newState(RadioState state);
  }

  private StateListener stateListener;
  private int ackPos;
  /* type = 2 (ACK), third byte needs to be sequence number... */
  private final int[] ackBuf = {0x05, 0x02, 0x00, 0x00, 0x00, 0x00};
  private boolean ackFramePending;
  private final CCITT_CRC rxCrc = new CCITT_CRC();
  private final CCITT_CRC txCrc = new CCITT_CRC();

  private final ArrayFIFO rxFIFO;

  public void setStateListener(StateListener listener) {
    stateListener = listener;
  }

  public RadioState getState() {
    return stateMachine;
  }

  public Transceiver(MSP430Core cpu) {
    super("FWPX Transceiver", "Radio", cpu);
    rxFIFO = new ArrayFIFO("RXFIFO", memory, RAM_RXFIFO, 128);

    registers[REG_SNOP] = 0;
    registers[REG_TXCTRL] = 0xa0ff;
    setModeNames(MODE_NAMES);
    setMode(MODE_POWER_OFF);
    currentFIFOP = false;
    rxFIFO.reset();
    reset();
  }

  private void reset() {
    setReg(REG_MDMCTRL0, 0x0ae2);
    registers[REG_RSSI] =  0xE000 | (registers[REG_RSSI]  & 0xFF);
  }

  private boolean setState(RadioState state) {
    if(logLevel > INFO) log("State transition from " + stateMachine + " to " + state);
    stateMachine = state;
    /* write to FSM state register */
    registers[REG_FSMSTATE] = state.getFSMState();

    switch(stateMachine) {

      case VREG_OFF:
        if (logLevel > INFO) log("VREG Off.");
        flushRX();
        flushTX();
        status &= ~(STATUS_RSSI_VALID | STATUS_XOSC16M_STABLE);
        crcOk = false;
        reset();
        setMode(MODE_POWER_OFF);
        break;

      case POWER_DOWN:
        rxFIFO.reset();
        status &= ~(STATUS_RSSI_VALID | STATUS_XOSC16M_STABLE);
        crcOk = false;
        reset();
        setMode(MODE_POWER_OFF);
        break;

      case RX_CALIBRATE:
        /* should be 12 according to specification */
        setSymbolEvent(12);
        setMode(MODE_RX_ON);
        break;
      case RX_SFD_SEARCH:
        zeroSymbols = 0;
        /* eight symbols after first SFD search RSSI will be valid */
        if ((status & STATUS_RSSI_VALID) == 0) {
          setSymbolEvent(8);
        }
//      status |= STATUS_RSSI_VALID;
        setMode(MODE_RX_ON);
        break;

      case RX_WAIT:
        setSymbolEvent(8);
        setMode(MODE_RX_ON);
        break;

      case IDLE:
        status &= ~STATUS_RSSI_VALID;
        setMode(MODE_TXRX_OFF);
        break;

      case RX_FRAME:
        /* mark position of frame start - for rejecting when address is wrong */
        rxFIFO.mark();
        rxread = 0;
        shouldAck = false;
        crcOk = false;
        break;
    }

    /* Notify state listener */
    if (stateListener != null) {
      stateListener.newState(stateMachine);
    }
    stateChanged(stateMachine.state);

    return true;
  }

  /**
   * Generate a new packet from the TX FIFIO
   */
  private void pendTxFromFifo(boolean txOnCca) {
    final byte channel = (byte) (memory[RAM_TXFIFO] & 0xff);
    byte len = (byte) (memory[RAM_TXFIFO + 1] & 0xff);
    final byte maxlen = TXFIFO_SZ - 2;
    if (len > maxlen) {
      logger.logw(this, WarningType.EXECUTION, "Transceiver: Warning - packet size too large: " + (len & 0xff));
      len = maxlen;
    }

    byte data[] = new byte[len];
    for (int i = 0; i < len; i++)
      data[i] = (byte) (memory[RAM_TXFIFO + 2 + i] & 0xff);

    var cm = getChannel(channel);
    cm.pendTx(data, txOnCca);

    txfifoFlush = true;
  }

  @Override
  public void dataReceived(USARTSource source, int data) {
    int oldStatus = status;
    if (logLevel > INFO) {
      log("byte received: " + Utils.hex8(data) +
          " (" + ((data >= ' ' && data <= 'Z') ? (char) data : '.') + ')' +
          " CS: " + chipSelect + " SPI state: " + state + " StateMachine: " + stateMachine);
    }

    if (!chipSelect) {
      // Chip is not selected

    } else if (stateMachine != RadioState.VREG_OFF) {
      switch (state) {
        case WAITING:
          if ((data & FLAG_READ) != 0) {
            state = SpiState.READ_REGISTER;
          } else {
            state = SpiState.WRITE_REGISTER;
          }
          if ((data & FLAG_RAM) != 0) {
            state = SpiState.RAM_ACCESS;
            usartDataAddress = data & 0x7f;
          } else {
            // The register address
            usartDataAddress = data & 0x3f;

            if (usartDataAddress == REG_RXFIFO) {
              // check read/write???
              //          log("Reading RXFIFO!!!");
              state = SpiState.READ_RXFIFO;
            } else if (usartDataAddress == REG_TXFIFO) {
              state = SpiState.WRITE_TXFIFO;
            }
          }
          if (data < 0x0f) {
            strobe(data);
            state = SpiState.WAITING;
          }
          usartDataPos = 0;
          // Assuming that the status always is sent back???
          //source.byteReceived(status);
          break;

        case WRITE_REGISTER:
          if (usartDataPos == 0) {
            source.byteReceived(registers[usartDataAddress] >> 8);
            // set the high bits
            usartDataValue = data << 8;
            // registers[usartDataAddress] = (registers[usartDataAddress] & 0xff) | (data << 8);
            usartDataPos = 1;
          } else {
            source.byteReceived(registers[usartDataAddress] & 0xff);
            // set the low bits
            usartDataValue |= data;
            // registers[usartDataAddress] = (registers[usartDataAddress] & 0xff00) | data;

            if (logLevel > INFO) {
              log("wrote to " + Utils.hex8(usartDataAddress) + " = " + usartDataValue);
            }
            setReg(usartDataAddress, usartDataValue);
            /* register written - go back to waiting... */
            state = SpiState.WAITING;
          }
          break;
        case READ_REGISTER:
          if (usartDataPos == 0) {
            source.byteReceived(registers[usartDataAddress] >> 8);
            usartDataPos = 1;
          } else {
            source.byteReceived(registers[usartDataAddress] & 0xff);
            if (logLevel > INFO) {
              log("read from " + Utils.hex8(usartDataAddress) + " = "
                  + registers[usartDataAddress]);
            }
            state = SpiState.WAITING;
          }
          return;
        //break;
        case READ_RXFIFO: {
          int fifoData = rxFIFO.read();
          if (logLevel > INFO) log("RXFIFO READ: " + rxFIFO.stateToString());
          source.byteReceived(fifoData);

          /* Clear FIFOP after the first byte is read. No threshold anymore. */
          if (currentFIFOP) {
            setFIFOP(false);
            if (logLevel > INFO) log("*** FIFOP cleared at: " + rxFIFO.stateToString());
          }

          /* Try to initiate read of another packet. tryNewDelivery can always be called safely
           * because we will check the status */
          tryNewDelivery();
        }
        return; /* avoid returning the status byte */
        case WRITE_TXFIFO:
          if(txfifoFlush) {
            txCursor = 0;
            txfifoFlush = false;
          }
          if (logLevel > INFO) log("Writing data: " + data + " to tx: " + txCursor);

          if(txCursor == 0) {
            if ((data & 0xff) > 127) {
              logger.logw(this, WarningType.EXECUTION, "CC2420: Warning - packet size too large: " + (data & 0xff));
            }
          } else if (txCursor > 127) {
            logger.logw(this, WarningType.EXECUTION, "CC2420: Warning - TX Cursor wrapped");
            txCursor = 0;
          }
          memory[RAM_TXFIFO + txCursor] = data & 0xff;
          txCursor++;
          if (sendEvents) {
            sendEvent("WRITE_TXFIFO", null);
          }
          break;
        case RAM_ACCESS:
          if (usartDataPos == 0) {
            usartDataAddress |= (data << 1) & 0x180;
            ramRead = (data & FLAG_RAM_READ) != 0;
            if (logLevel > INFO) {
              log("Address: " + Utils.hex16(usartDataAddress) + " read: " + ramRead);
            }
            usartDataPos++;
          } else {
            if (!ramRead) {
              memory[usartDataAddress++] = data;
              if (usartDataAddress >= 0x180) {
                logger.logw(this, WarningType.EXECUTION, "CC2420: Warning - RAM position too big - wrapping!");
                usartDataAddress = 0;
              }
              if (logLevel > INFO && usartDataAddress == RAM_PANID + 2) {
                log("Pan ID set to: 0x" +
                    Utils.hex8(memory[RAM_PANID]) +
                    Utils.hex8(memory[RAM_PANID + 1]));
              }
            } else {
              //log("Read RAM Addr: " + address + " Data: " + memory[address]);
              source.byteReceived(memory[usartDataAddress++]);
              if (usartDataAddress >= 0x180) {
                logger.logw(this, WarningType.EXECUTION, "CC2420: Warning - RAM position too big - wrapping!");
                usartDataAddress = 0;
              }
              return;
            }
          }
          break;
      }
      source.byteReceived(oldStatus);
    } else {
      /* No VREG but chip select */
      source.byteReceived(0);
      logw(WarningType.EXECUTION, "**** Warning - writing to CC2420 when VREG is off!!!");
    }
  }

  private void setReg(int address, int data) {
    int oldValue = registers[address];
    switch (address) {
      case REG_RSSI:
        registers[address] = (registers[address] & 0xFF) | (data & 0xFF00);
        break;
      default:
        registers[address] = data;
    }
    switch (address) {
      case REG_IOCFG0:
        fifopThr = data & FIFOP_THR;
        if (logLevel > INFO) log("IOCFG0: 0x" + Utils.hex16(oldValue) + " => 0x" + Utils.hex16(data));
        if ((oldValue & POLARITY_MASK) != (data & POLARITY_MASK)) {
          // Polarity has changed - must update pins
          setFIFOP(currentFIFOP);
          setFIFO(currentFIFO);
          setSFD(currentSFD);
          setCCA(currentCCA);
        }
        break;
      case REG_IOCFG1:
        if (logLevel > INFO)
          log("IOCFG1: SFDMUX "
              + ((registers[address] & SFDMUX) >> SFDMUX_SHIFT)
              + " CCAMUX: " + ((registers[address] & CCAMUX) >> CCAMUX_SHIFT));
        break;
      case REG_MDMCTRL0:
        addressDecode = (data & ADR_DECODE) != 0;
        autoCRC = (data & ADR_AUTOCRC) != 0;
        autoAck = (data & AUTOACK) != 0;
        break;
      case REG_FSCTRL: {
        logw(WarningType.EXECUTION, "setReg(FSCTRL) not supported");
        return;
      }
    }
    configurationChanged(address, oldValue, data);
  }

  // Needs to get information about when it is possible to write
  // next data...
  private void strobe(int data) {
    // Resets, on/off of different things...
    if (logLevel > INFO) {
      log("Strobe on: " + Utils.hex8(data) + " => " + Reg.values()[data]);
    }

    if ((stateMachine == RadioState.POWER_DOWN) && (data != REG_SXOSCON)) {
      if (logLevel > INFO) log("Got command strobe: " + data + " in POWER_DOWN.  Ignoring.");
      return;
    }

    switch (data) {
      case REG_SNOP:
        if (logLevel > INFO) log("SNOP => " + Utils.hex8(status) + " at " + cpu.cycles);
        break;
      case REG_SRXON:
        if(stateMachine == RadioState.IDLE) {
          setState(RadioState.RX_CALIBRATE);
          //updateActiveFrequency();
          if (logLevel > INFO) {
            log("Strobe RX-ON!!!");
          }
        } else {
          if (logLevel > INFO) log("WARNING: SRXON when not IDLE");
        }

        break;
      case REG_SRFOFF:
        if (logLevel > INFO) {
          log("Strobe RXTX-OFF!!! at " + cpu.cycles);
          if (/* TODO */stateMachine == RadioState.RX_FRAME) {
            log("WARNING: turning off RXTX during " + stateMachine);
          }
        }
        setState(RadioState.IDLE);
        break;
      case REG_STXON:
        // State transition valid from IDLE state or all RX states
        if (/* TODO */(stateMachine != RadioState.VREG_OFF &&
             stateMachine != RadioState.POWER_DOWN)) {
          if (sendEvents) {
            sendEvent("STXON", null);
          }
          pendTxFromFifo(false);
        }
        break;
      case REG_STXONCCA:
        if (/* TODO */(stateMachine != RadioState.VREG_OFF &&
            stateMachine != RadioState.POWER_DOWN)) {
          if (sendEvents) {
            sendEvent("STXON_CCA", null);
          }
          pendTxFromFifo(true);
        }
        break;
      case REG_SFLUSHRX:
        flushRX();
        break;
      case REG_SFLUSHTX:
        if (logLevel > INFO) log("Flushing TXFIFO");
        flushTX();
        break;
      case REG_SXOSCON:
        //log("Strobe Oscillator On");
        startOscillator();
        break;
      case REG_SXOSCOFF:
        //log("Strobe Oscillator Off");
        stopOscillator();
        break;
      case REG_SACK:
      case REG_SACKPEND:
        logw(WarningType.EXECUTION, "Not implemented");
        break;
      default:
        if (logLevel > INFO) {
          log("Unknown strobe command: " + data);
        }
        break;
    }
  }

  private void setSymbolEvent(int symbols) {
    double period = SYMBOL_PERIOD * symbols;
    cpu.scheduleTimeEventMillis(symbolEvent, period);
    //log("Set Symbol event: " + period);
  }

  private void startOscillator() {
    // 1ms crystal startup from datasheet pg12
    cpu.scheduleTimeEventMillis(oscillatorEvent, 1);
  }

  private void stopOscillator() {
    status &= ~STATUS_XOSC16M_STABLE;
    setState(RadioState.POWER_DOWN);
    if (logLevel > INFO) log("Oscillator Off.");
    // Reset state
    setFIFOP(false);
  }

  private void flushRX() {
    if (logLevel > INFO) {
      log("Flushing RX len = " + rxFIFO.length());
    }
    rxFIFO.reset();
    setSFD(false);
    setFIFOP(false);
    setFIFO(false);
    /* goto RX Calibrate */
    if( (stateMachine == RadioState.RX_CALIBRATE) ||
        (stateMachine == RadioState.RX_SFD_SEARCH) ||
        (stateMachine == RadioState.RX_FRAME) ||
        (stateMachine == RadioState.RX_OVERFLOW) ||
        (stateMachine == RadioState.RX_WAIT)) {
      setState(RadioState.RX_SFD_SEARCH);
    }

    tryNewDelivery();
  }

  // TODO: update any pins here?
  private void flushTX() {
    txCursor = 0;
  }

  private void setInternalCCA(boolean clear) {
    setCCA(clear);
    if (logLevel > INFO) log("Internal CCA: " + clear);
  }

  private void setSFD(boolean sfd) {
    currentSFD = sfd;
    if( (registers[REG_IOCFG0] & SFD_POLARITY) == SFD_POLARITY)
      sfdPort.setPinState(sfdPin, sfd ? IOPort.PinState.LOW : IOPort.PinState.HI);
    else
      sfdPort.setPinState(sfdPin, sfd ? IOPort.PinState.HI : IOPort.PinState.LOW);
    if (logLevel > INFO) log("SFD: " + sfd + "  " + cpu.cycles);
  }

  private void setCCA(boolean cca) {
    currentCCA = cca;
    if (logLevel > INFO) log("Setting CCA to: " + cca);
    if( (registers[REG_IOCFG0] & CCA_POLARITY) == CCA_POLARITY)
      ccaPort.setPinState(ccaPin, cca ? IOPort.PinState.LOW : IOPort.PinState.HI);
    else
      ccaPort.setPinState(ccaPin, cca ? IOPort.PinState.HI : IOPort.PinState.LOW);
  }

  private void setFIFOP(boolean fifop) {
    currentFIFOP = fifop;
    if (logLevel > INFO) log("Setting FIFOP to " + fifop);
    if( (registers[REG_IOCFG0] & FIFOP_POLARITY) == FIFOP_POLARITY) {
      fifopPort.setPinState(fifopPin, fifop ? IOPort.PinState.LOW : IOPort.PinState.HI);
    } else {
      fifopPort.setPinState(fifopPin, fifop ? IOPort.PinState.HI : IOPort.PinState.LOW);
    }
  }

  private void setFIFO(boolean fifo) {
    currentFIFO = fifo;
    if (logLevel > INFO) log("Setting FIFO to " + fifo);
    if((registers[REG_IOCFG0] & FIFO_POLARITY) == FIFO_POLARITY) {
      fifoPort.setPinState(fifoPin, fifo ? IOPort.PinState.LOW : IOPort.PinState.HI);
    } else {
      fifoPort.setPinState(fifoPin, fifo ? IOPort.PinState.HI : IOPort.PinState.LOW);
    }
  }

  private void setRxOverflow() {
    if (logLevel > INFO) log("RXFIFO Overflow! Read Pos: " + rxFIFO.stateToString());
    setFIFOP(true);
    setFIFO(false);
    setSFD(false);
    shouldAck = false;
    setState(RadioState.RX_OVERFLOW);
  }


  /*****************************************************************************
   *  External APIs for simulators simulating Radio medium, etc.
   *
   *****************************************************************************/

  public int getOutputPowerIndicator() {
    return (registers[REG_TXCTRL] & 0x1f);
  }

  public int getOutputPowerIndicatorMax() {
    return 31;
  }

  /**
   * This is actually the "CORR" value.
   * @param lqi The Corr-val
   * @see "CC2420 Datasheet"
   */
  public void setLQI(int lqi){
    if(lqi < 0) lqi = 0;
    else if(lqi > 0x7f ) lqi = 0x7f;
    corrval = lqi;
  }

  public int getLQI() {
    return corrval;
  }

  public void setRSSI(int power) {
    final int minp = -128 + RSSI_OFFSET;
    final int maxp = 127 + RSSI_OFFSET;
    if (power < minp) {
      power = -minp;
    }
    if(power > maxp){
      power = maxp;
    }

    if (logLevel > INFO) log("external setRSSI to: " + power);

    rssi = power;
    registers[REG_RSSI] = (registers[REG_RSSI] & 0xFF00) | ((power - RSSI_OFFSET) & 0xFF);
  }

  public int getRSSI() {
    return rssi;
  }

  public int getOutputPower() {
    /* From CC2420 datasheet */
    int indicator = getOutputPowerIndicator();
    if (indicator >= 31) {
      return 0;
    } else if (indicator >= 27) {
      return -1;
    } else if (indicator >= 23) {
      return -3;
    } else if (indicator >= 19) {
      return -5;
    } else if (indicator >= 15) {
      return -7;
    } else if (indicator >= 11) {
      return -10;
    } else if (indicator >= 7) {
      return -15;
    } else if (indicator >= 3) {
      return -25;
    }

    /* Unknown */
    return -100;
  }

  public int getOutputPowerMax() {
    return 0;
  }

  @Override
  public void notifyReset() {
    super.notifyReset();
    setChipSelect(false);
    status &= ~STATUS_TX_ACTIVE;
    setVRegOn(false);
  }

  public void setVRegOn(boolean newOn) {
    if(on == newOn) return;

    if(newOn) {
      // 0.6ms maximum vreg startup from datasheet pg 13
      // but Z1 platform does not work with 0.1 so trying with lower...
      cpu.scheduleTimeEventMillis(vregEvent, 0.05);
      if (logLevel > INFO) log("Scheduling vregEvent at: cyc = " + cpu.cycles +
          " target: " + vregEvent.getTime() + " current: " + cpu.getTime());
    } else {
      on = false;
      setState(RadioState.VREG_OFF);
    }
  }

  public void setChipSelect(boolean select) {
    chipSelect = select;
    if (!chipSelect) {
      if (state == SpiState.WRITE_REGISTER && usartDataPos == 1) {
        // Register write incomplete. Do an 8 bit register write.
        usartDataValue = (registers[usartDataAddress] & 0xff) | (usartDataValue & 0xff00);
        if (logLevel > INFO) {
          log("wrote 8 MSB to 0x" + Utils.hex8(usartDataAddress) + " = " + usartDataValue);
        }
        setReg(usartDataAddress, usartDataValue);
      }
      state = SpiState.WAITING;
    }

    if (logLevel > INFO) {
      log("setting chipSelect: " + chipSelect);
    }
  }

  @Override
  public boolean getChipSelect() {
    return chipSelect;
  }

  public void setCCAPort(IOPort port, int pin) {
    ccaPort = port;
    ccaPin = pin;
  }

  public void setFIFOPPort(IOPort port, int pin) {
    fifopPort = port;
    fifopPin = pin;
  }

  public void setFIFOPort(IOPort port, int pin) {
    fifoPort = port;
    fifoPin = pin;
  }

  public void setSFDPort(IOPort port, int pin) {
    sfdPort = port;
    sfdPin = pin;
  }

  public boolean isReceiving() {
    for (var buf: channels.values()) {
      if (buf.isReceiving()) return true;
    }
    return false;
  }

  // -------------------------------------------------------------------
  // Methods for accessing and writing to registers, etc. from outside
  // And for receiving data
  // -------------------------------------------------------------------

  public int getRegister(int register) {
    return registers[register];
  }

  public void setRegister(int register, int data) {
    registers[register] = data;
  }

  /*****************************************************************************
   * The Packet Layer
   *****************************************************************************/
  private static class ChannelManager {
    private final Transceiver trx;
    private final byte channel;
    private final Random random;

    ChannelManager(Transceiver trx, byte channel, int seed) {
      this.trx = trx;
      this.channel = channel;
      this.random = new Random(seed);
      clearRx();
    }

    /* RX */
    private final List<PacketId> startedReceptions = new ArrayList<>();
    private final Set<PacketId> ongoingReceptions = new HashSet<>();
    private final Map<PacketId, Packet> finishedReceptions = new HashMap<>();

    private List<Packet> decodedPackets;
    private byte detectedPacketNum;

    /* TX */
    private enum TxState {
      IDLE,
      BACKOFF,
      CALIBRATE,
      PREAMBLE,
      FRAME,
    }

    private double backoffBase = 60 * SYMBOL_PERIOD;
    private int backoffExp = 3;
    private TxState txState = TxState.IDLE;
    private long txCounter = 0;
    private final Queue<Packet> txQueue = new LinkedList<>();
    private final Queue<Boolean> txOnCca = new LinkedList<>();

    private final TimeEvent txBackoffDoneEvent = new TimeEvent(0, "TX Backoff Done") {
      @Override
      public void execute(long t) {
        assert txState == TxState.BACKOFF && !txQueue.isEmpty() && txOnCca.peek() == true;
        if (isReceiving()) {
          /* Start another backoff. */
          startBackoff();
        } else {
          /* Otherwise, start transmission. */
          doTx();
        }
      }
    };

    private final TimeEvent txCalibrationDoneEvent = new TimeEvent(0, "TX Calibration Done") {
      @Override
      public void execute(long t) {
        assert txState == TxState.CALIBRATE;
        txState = TxState.PREAMBLE;
        trx.channelTxStart(txQueue.peek().id());
        trx.cpu.scheduleTimeEventMillis(txPreambleDoneEvent, 12 * SYMBOL_PERIOD);
      }
    };

    private final TimeEvent txPreambleDoneEvent = new TimeEvent(0, "TX Preamble Done") {
      @Override
      public void execute(long t) {
        assert txState == TxState.PREAMBLE;
        txState = TxState.FRAME;
        /* We do not actually do CRC here, but add the transmission latency for the 2 CRC bytes. */
        final double txLatency = (txQueue.peek().data().length + 2) * 2 * SYMBOL_PERIOD;
        trx.cpu.scheduleTimeEventMillis(txFrameDoneEvent, 12 * txLatency);
      }
    };

    private final TimeEvent txFrameDoneEvent = new TimeEvent(0, "TX Frame Done") {
      @Override
      public void execute(long t) {
        assert txState == TxState.FRAME;
        txState = TxState.IDLE;
        trx.channelTxFinished(txQueue.peek().id(), txQueue.peek());
        txQueue.remove();
        txOnCca.remove();
      }
    };

    void pendTx(byte[] data, boolean onCca) {
      txQueue.add(new Packet(new PacketId(trx, channel, txCounter++), data));
      txOnCca.add(onCca);
      tryTx();
    }

    void tryTx() {
      if (txState == TxState.IDLE && !txQueue.isEmpty()) {
        if (txOnCca.peek()) {
          if (isReceiving()) {
            startBackoff();
          } else {
            doTx();
          }
        } else {
          doTx();
        }
      }
    }

    private void startBackoff() {
      txState = TxState.BACKOFF;
      double backoff = random.nextInt(2 << backoffExp) * backoffBase;
      trx.cpu.scheduleTimeEventMillis(txBackoffDoneEvent, backoff);
    }

    private void doTx() {
      assert txState == TxState.BACKOFF || txState == TxState.IDLE || !txQueue.isEmpty();
      txState = TxState.CALIBRATE;
      trx.cpu.scheduleTimeEventMillis(txCalibrationDoneEvent, 12 * SYMBOL_PERIOD);
    }

    void clearRx() {
      startedReceptions.clear();
      ongoingReceptions.clear();
      finishedReceptions.clear();
      decodedPackets = null;
      detectedPacketNum = 0;
    }

    boolean isReceiving() {
      return !ongoingReceptions.isEmpty();
    }

    void receptionStarted(PacketId id) {
      startedReceptions.add(id);
      ongoingReceptions.add(id);
    }

    void receptionFinished(PacketId id, Packet packet) {
      if (ongoingReceptions.contains(id)) {
        ongoingReceptions.remove(id);
        finishedReceptions.put(id, packet);
      }
    }

    boolean decode() {
      assert !isReceiving();

      final double p = random.nextDouble();
      boolean success = true;

      decodedPackets = null;
      /* Fail to detect the packet number. */
      if (p > getDetectionSuccessProbability()) {
        detectedPacketNum = (byte)(random.nextInt(startedReceptions.size()) & 0xff);
        success = false;
      }

      /* Correctly detect the collided packet number, but fails to decode. */
      if (success)
        detectedPacketNum = (byte)startedReceptions.size();
      if (p > getDecodingSuccessProbability())
        success = false;
      if (!success) {
        if (trx.logLevel > INFO)
          trx.log(String.format("Detected {}/{} packets", detectedPacketNum, startedReceptions.size()));
        return false;
      }

      /* Successfully decode the packets. */
      if (trx.logLevel > INFO) trx.log(String.format("Decoded {} packets", detectedPacketNum));
      List<Packet> packets = new ArrayList<>();
      for (PacketId id : startedReceptions) {
        Packet pkt = finishedReceptions.get(id);
        packets.add(pkt);
      }
      decodedPackets = packets;
      return true;
    }

    List<Packet> getDecodedPacket() {
      return decodedPackets;
    }

    byte getDetectedPacketNum() {
      return detectedPacketNum;
    }

    private double getDecodingSuccessProbability() {
      switch(startedReceptions.size()) {
        case 1:
          return 0.99;
        case 2:
          return 0.80;
        default:
          return 0.0;
      }
    }

    private double getDetectionSuccessProbability() {
      switch(startedReceptions.size()) {
        case 1:
          return 1.0;
        case 2:
        case 3:
        case 4:
          return 1.0 - 0.03 * startedReceptions.size();
        default:
          return 0.0;
      }
    }
  }

  private static abstract class Deliverable {
    static final char PACKET = 1;
    static final char PACKET_NUM = 2;

    abstract byte[] toBinary();
  }

  private static class DecodedPacket extends Deliverable {
    final byte channel;
    final byte[] data;
    private byte[] bin;
    DecodedPacket(Packet pkt) {
      this.channel = pkt.id().channel();
      this.data = pkt.data();
      this.bin = null;
    }
    @Override
    byte[] toBinary() {
      if (bin == null) {
        bin = new byte[data.length + 3];
        bin[0] = PACKET;
        bin[1] = channel;
        bin[2] = (byte) data.length;
        System.arraycopy(data, 0, bin, 3, data.length);
      }
      return bin;
    }
  }

  private static class DetectedPacketNum extends Deliverable {
    final byte channel;
    final byte num;
    private byte[] bin;
    DetectedPacketNum(byte channel, byte num) {
      this.channel = channel;
      this.num = num;
      this.bin = null;
    }
    @Override
    byte[] toBinary() {
      if (bin == null) {
        bin = new byte[3];
        bin[0] = PACKET_NUM;
        bin[1] = channel;
        bin[2] = num;
      }
      return bin;
    }
  }

  private final Map<Byte, ChannelManager> channels = new HashMap<>();

  private final Queue<Deliverable> deliverableQ = new LinkedList<>();

  private ChannelManager getChannel(byte c) {
    if (!channels.containsKey(c))
      channels.put(c, new ChannelManager(Transceiver.this, c, this.hashCode() + c));
    return channels.get(c);
  }

  private void channelPendDeliverable(Deliverable deliverable) {
    deliverableQ.add(deliverable);
    tryNewDelivery();
  }

  private void channelTxStart(PacketId id) {
    if (logLevel > INFO) log("TX started: " + id);
    if (packetListener != null) {
      /* Notify the listeners of the delivery start. */
      packetListener.packetDeliveryStarted(id);
    }
  }

  private void channelTxFinished(PacketId id, Packet pkt) {
    if (logLevel > INFO) log("TX finished: " + pkt.id());
    if (packetListener != null) {
      /* Notify the listeners of the delivery end. */
      packetListener.packetDeliveryFinished(id, pkt);
    }
  }

  private boolean tryNewDelivery() {
    /* We only do the delivery when we can issue an interrupt at once. The
     * behaviour will be the same seeing from outside, but the implementation logic
     * can be more concise for us. Also, FIFOP threshold is not needed now.
     *
     * With current design, actually we will not run into this loop more than once.
     */
    boolean delivered = false;
    while (rxFIFO.isEmpty() && !deliverableQ.isEmpty()) {
      final var d = deliverableQ.peek();
      final var binLen = d.toBinary().length;
      if (binLen > rxFIFO.size()) {
        logw(WarningType.EXECUTION, "Transceiver: Warning - deliverable larger than rxFIFO size. Aborting.");
        deliverableQ.remove();
        continue;
      }

      /* Let's put the deliverable into the rxFIFO. */
      for (byte b: d.toBinary())
        rxFIFO.write(b);

      /* Notify the firmware with FIFOP interrupt. */
      assert currentFIFOP == false;
      setFIFOP(true);

      /* Remove the deliverable from the pending queue. */
      deliverableQ.remove();
      delivered = true;
    }
    return delivered;
  }

  /*****************************************************************************
   * PacketListener APIs
   *****************************************************************************/
  @Override
  public void packetDeliveryStarted(PacketId id) {
    final byte channel = id.channel();
    getChannel(channel).receptionStarted(id);
    /* TODO: If SFD interrupt is needed for the firmware, we may need to set it
     * here. But still not quite sure how to combine this with packet number detection
     * success probability when there is collision.
     */
  }

  @Override
  public void packetDeliveryFinished(PacketId id, Packet packet) {
    final byte channel = id.channel();
    ChannelManager cbuf = channels.get(channel);
    cbuf.receptionFinished(id, packet);

    if (!cbuf.isReceiving()) {
      var decoded = cbuf.decode();
      if (decoded) {
        for (var pkt: cbuf.getDecodedPacket())
          channelPendDeliverable(new DecodedPacket(pkt));
      } else {
        channelPendDeliverable(new DetectedPacketNum(channel, cbuf.getDetectedPacketNum()));
      }
      cbuf.clearRx();
    }
  }

  /*****************************************************************************
   * PacketSource APIs
   *****************************************************************************/
  private PacketListener packetListener = null;

  @Override
  public void addPacketListener(PacketListener p) {
    packetListener = PacketListener.Proxy.INSTANCE.add(packetListener, p);
  }

  @Override
  public void removePacketListener(PacketListener p) {
    packetListener = PacketListener.Proxy.INSTANCE.remove(packetListener, p);
  }

  /*****************************************************************************
   * Chip APIs
   *****************************************************************************/

  @Override
  public int getModeMax() {
    return MODE_MAX;
  }

  private String getLongAddress() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      if ((i % 2 == 0) && i > 0) {
        sb.append(':');
      }
      sb.append(Utils.hex8(memory[RAM_IEEEADDR + 7 - i]));
    }
    return sb.toString();
  }

  @Override
  public String info() {
    return " VREG_ON: " + on + "  Chip Select: " + chipSelect +
        "  OSC Stable: " + ((status & STATUS_XOSC16M_STABLE) > 0) +
        "\n RSSI Valid: " + ((status & STATUS_RSSI_VALID) > 0) +
        "\n FIFOP: " + currentFIFOP + " threshold: " + fifopThr +
        " polarity: " + ((registers[REG_IOCFG0] & FIFOP_POLARITY) == FIFOP_POLARITY) +
        "  FIFO: " + currentFIFO + "  SFD: " + currentSFD +
        "\n " + rxFIFO.stateToString() + " expPacketLen: " + rxlen +
        "\n Radio State: " + stateMachine + "  SPI State: " + state +
        "\n AutoACK: " + autoAck + "  AddrDecode: " + addressDecode + "  AutoCRC: " + autoCRC +
        "\n PanID: 0x" + Utils.hex8(memory[RAM_PANID + 1]) + Utils.hex8(memory[RAM_PANID]) +
        "  ShortAddr: 0x" + Utils.hex8(memory[RAM_SHORTADDR + 1]) + Utils.hex8(memory[RAM_SHORTADDR]) +
        "  LongAddr: 0x" + getLongAddress() +
        "\n Channel: " + activeChannel +
        "  Output Power: " + getOutputPower() + "dB (" + getOutputPowerIndicator() + '/' + getOutputPowerIndicatorMax() +
        ")\n";
  }

  @Override
  public void stateChanged(int state) {
  }
} /* Transceiver */