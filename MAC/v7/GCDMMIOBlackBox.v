// DOC include start: GCD portlist
module GCDMMIOBlackBox
  #(parameter WIDTH)
   (
    input                  clock,
    input                  reset,
    output                 input_ready,
    input                  input_valid,
    input [WIDTH-1:0]      x,
    input [WIDTH-1:0]      y,
    input [WIDTH-1:0]      x2,
    input [WIDTH-1:0]      y2,
    input [WIDTH-1:0]      prev,
    input                  output_ready,
    output                 output_valid,
    output reg [WIDTH-1:0] gcd,
    output                 busy
    );
// DOC include end: GCD portlist

   localparam S_IDLE = 2'b00, S_RUN = 2'b01, S_DONE = 2'b10;

   reg [1:0]               state , next;   
   reg [WIDTH-1:0]         tmp;

   
   reg [WIDTH-1:0]         product1;
   reg [WIDTH-1:0]         product2;
   
   always @(posedge clock) begin
      if (reset) state <= S_IDLE;
      else        state <= next;

   end
  
   always @ (*) begin
      
      case (state)
      S_IDLE: begin 
         if (input_ready) begin 
            product1 = x*y;
            product2 = x2*y2;
            gcd = 0;
            tmp = gcd;
         end
         if (input_valid) next = S_RUN;
         else             next = S_IDLE;
      end
      S_RUN: begin 
            tmp = gcd + product1 + product2;
            gcd = tmp;
            next = S_DONE; 
      end
      S_DONE: begin if (output_ready) next = S_IDLE;
            else next = S_DONE;
      end

      default: next = S_IDLE;
      endcase
   



   end

   assign input_ready = state == S_IDLE;
   assign busy = state != S_IDLE;
   assign output_valid = state == S_DONE;
   

endmodule // GCDMMIOBlackBox
