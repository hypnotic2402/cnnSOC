package chipyard.example

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf

case class MACParams(
  address: BigInt = 0x1000,
  width: Int = 32,
  useAXI4: Boolean = false)

case object MACKey extends Field[Option[MACParams]](None)

class MACIO(val w: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val x = Input(UInt(w.W))
  val y = Input(UInt(w.W))
  val mac = Output(UInt(w.W))
  val busy = Output(Bool())
}

trait MACTopIO extends Bundle {
  val mac_busy = Output(Bool())
}

trait HasMACIO extends BaseModule {
  val w: Int
  val io = IO(new MACIO(w))
}

class MACMMIOBlackBox(val w: Int) extends BlackBox(Map("WIDTH" -> IntParam(w))) with HasBlackBoxResource
  with HasMACIO
{
  addResource("/vsrc/MACMMIOBlackBox.v")
}

trait MACModule extends HasRegMap {
  val io: MACTopIO

  implicit val p: Parameters
  def params: MACParams
  val clock: Clock
  val reset: Reset

  val x = Reg(UInt(params.width.W))
  val y = Reg(UInt(params.width.W))
  val mac = Reg(UInt(params.width.W))

  impl = Module(new GCDMMIOBlackBox(params.width))

  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.x := x
  impl.io.y := y
  
  mac := impl.io.mac

  io.gcd_busy := impl.io.busy

  regmap(
    // 0x00 -> Seq(
    //   RegField.r(2, status)), // a read-only register capturing current status
    0x04 -> Seq(
      RegField.w(params.width, x)), // a plain, write-only register
    0x08 -> Seq(
      RegField.w(params.width, y)), // write-only, y.valid is set on write
    0x0C -> Seq(
      RegField.r(params.width, mac))) // read-only, gcd.ready is set on read
}

class MACTL(params: MACParams, beatBytes: Int)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "mac", Seq("ucbbar,mac"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with MACTopIO)(
      new TLRegModule(params, _, _) with MACModule)

class MACAXI4(params: MACParams, beatBytes: Int)(implicit p: Parameters)
  extends AXI4RegisterRouter(
    params.address,
    beatBytes=beatBytes)(
      new AXI4RegBundle(params, _) with MACTopIO)(
      new AXI4RegModule(params, _, _) with MACModule)


trait CanHavePeripheryMAC { this: BaseSubsystem =>
  private val portName = "mac"

  // Only build if we are using the TL (nonAXI4) version
  val mac = p(MACKey) match {
    case Some(params) => {
      if (params.useAXI4) {
        val mac = LazyModule(new MACAXI4(params, pbus.beatBytes)(p))
        pbus.toSlave(Some(portName)) {
          mac.node :=
          AXI4Buffer () :=
          TLToAXI4 () :=
          // toVariableWidthSlave doesn't use holdFirstDeny, which TLToAXI4() needsx
          TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true)
        }
        Some(mac)
      } else {
        val mac = LazyModule(new MACTL(params, pbus.beatBytes)(p))
        pbus.toVariableWidthSlave(Some(portName)) { mac.node }
        Some(mac)
      }
    }
    case None => None
  }
}

trait CanHavePeripheryMACModuleImp extends LazyModuleImp {
  val outer: CanHavePeripheryMAC
  val mac_busy = outer.mac match {
    case Some(mac) => {
      val busy = IO(Output(Bool()))
      busy := mac.module.io.mac_busy
      Some(busy)
    }
    case None => None
  }
}

class WithMAC(useAXI4: Boolean = false) extends Config((site, here, up) => {
  case MACKey => Some(MACParams(useAXI4 = useAXI4))
})
