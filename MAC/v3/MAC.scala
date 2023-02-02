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
  useAXI4: Boolean = false,
  useBlackBox: Boolean = false)

case object MACKey extends Field[Option[MACParams]](None)

class MACIO(val w: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val input_ready = Output(Bool())
  val input_valid = Input(Bool())
  val x = Input(UInt(w.W))
  val y = Input(UInt(w.W))
  val output_ready = Input(Bool())
  val output_valid = Output(Bool())
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

class MACMMIOChiselModule(val w: Int) extends Module
  with HasMACIO
{
  val s_idle :: s_run :: s_done :: Nil = Enum(3)

  val state = RegInit(s_idle)
  val done   = Reg(UInt(w.W))
  val mac   = Reg(UInt(w.W))

  io.input_ready := state === s_idle
  io.output_valid := state === s_done
  io.mac := mac

  when (state === s_idle && io.input_valid) {
    state := s_run
  } .elsewhen (state === s_run && done === 0.U) {
    state := s_done
  } .elsewhen (state === s_done && io.output_ready) {
    state := s_idle
  }

  when (state === s_idle && io.input_valid) {
    mac := 0.U
    done := 1.U
  } .elsewhen (state === s_run) {
    mac := mac + (io.x * io.y)
    done := 0.U
  }

  io.busy := state =/= s_idle
}

trait MACModule extends HasRegMap {
  val io: MACTopIO

  implicit val p: Parameters
  def params: MACParams
  val clock: Clock
  val reset: Reset

  val x = Wire(new DecoupledIO(UInt(params.width.W)))
  val y = Wire(new DecoupledIO(UInt(params.width.W)))
  val mac = Wire(new DecoupledIO(UInt(params.width.W)))
  val status = Wire(UInt(2.W))

  val impl = if (params.useBlackBox) {
    Module(new MACMMIOBlackBox(params.width))
  } else {
    Module(new MACMMIOChiselModule(params.width))
  }

  impl.io.clock := clock
  impl.io.reset := reset.asBool

  impl.io.x := x.bits
  impl.io.y := y.bits



  impl.io.input_valid := y.valid && x.valid
  x.ready := impl.io.input_ready
  y.ready := impl.io.input_ready


  
  mac.bits := impl.io.mac
  mac.valid := impl.io.output_valid
  impl.io.output_ready := mac.ready

  status := Cat(impl.io.input_ready, impl.io.output_valid)

  io.mac_busy := impl.io.busy

  regmap(
    0x00 -> Seq(
      RegField.r(2, status)), // a read-only register capturing current status
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
