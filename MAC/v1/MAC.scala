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


// MAC Params class

case class MACParams(
  address : BigInt = 0x1000,
  width : Int = 32
)

// MAC Key

case object MACKey extends Field[Option[MACParams]](None)

class MACIO(val w: Int) extends Bundle{
  val clock = Input(Clock())
  val reset = Input(Bool())
  val input_ready = Output(Bool())
  val input_valid = Input(Bool())
  val x = Input(SInt(w.W))
  val y = Input(SInt(w.W))
  val output_ready = Input(Bool())
  val output_valid = Output(Bool())
  val busy = Output(Bool())
  val mac = Output(SInt(w.W))
}

trait MACTopIO extends Bundle{
  val mac_busy = Output(Bool())
}

trait HasMACIO extends BaseModule {
  val w : Int
  val io = IO(new MACIO(w))
}

class MACMMIOBlackBox(val w : Int) extends BlackBox(Map("WIDTH" -> IntParam(w))) with HasBlackBoxResource
  with HasMACIO
{
  addResource("/vsrc/MACBlackBox.v")
}

trait MACModule extends HasRegMap {
  
 val io : MACTopIO

 implicit val p : Parameters
 def params : MACParams
 val clock : Clock
 val reset : Reset

 val x = Wire(new DecoupledIO(SInt(params.width.W)))
 val y = Wire(new DecoupledIO(SInt(params.width.W)))
 val mac = Wire(new DecoupledIO(SInt(params.width.W)))

 val status = Wire(UInt(2.W))

 val impl = new MACMMIOBlackBox(params.width)

 impl.io.clock := clock
 impl.io.reset := reset.asBool

 impl.io.x := x.bits
 impl.io.y := y.bits

 impl.io.input_valid := x.valid && y.valid
 x.ready := impl.io.input_ready
 y.ready := impl.io.input_ready

 mac.bits := impl.io.mac
 mac.valid := impl.io.output_valid
 impl.io.output_ready := mac.ready

 status := Cat(impl.io.input_ready , impl.io.output_valid)
 io.mac_busy := impl.io.busy

 regmap(
   0x00 -> Seq(
     RegField.r(2,status)),
   0x04 -> Seq(
     RegField.w(params.width , x)),
   0x08 -> Seq(
     RegField.w(params.width , y)),
   0x0C -> Seq(
     RegField.r(params.width , mac)))

}

class MACTL(params : MACParams , beatBytes : Int)(implicit p : Parameters)
  extends TLRegisterRouter(
    params.address , "mac" , Seq("ucbbar,mac"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with MACTopIO)(
      new TLRegModule(params, _, _) with MACModule)

      
trait CanHavePeripheryMAC { this : BaseSubsystem =>
  private val portName = "mac"
//  val t : Int
  val mac = p(MACKey) match {
    case Some(params) => {
       val mac = LazyModule(new MACTL(params , pbus.beatBytes)(p))
       pbus.toVariableWidthSlave(Some(portName)) { mac.node }
       Some(mac)
    
    }

    case None => None

  }
}


trait CanHavePeripheryMACModuleImp extends LazyModuleImp {
  val outer : CanHavePeripheryMAC
  val mac_busy = outer.mac match {
    case Some(mac) => {
      val busy = IO(Output(Bool()))
      busy := mac.module.io.mac_busy
      Some(busy)
    }
    case None => None
  }
}

class WithMAC extends Config((site , here ,up) => {
  case MACKey => Some(MACParams)
})
















