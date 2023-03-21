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
    input                  output_ready,
    output                 output_valid,
    output reg [WIDTH-1:0] gcd,
    output                 busy
    );
// DOC include end: GCD portlist

   localparam S_IDLE = 2'b00, S_RUN = 2'b01, S_DONE = 2'b10;

   reg [1:0]               state;   
   reg [WIDTH-1:0]         tmp;

   assign input_ready = state == S_IDLE;
   assign output_valid = state == S_DONE;
   assign busy = state != S_IDLE;
   reg [WIDTH-1:0]         product;
   
   
   
  

   // always @(posedge clock) begin
   //    if (reset)
   //      state <= S_IDLE;
   //    else if (state == S_IDLE && input_valid)
   //      state <= S_RUN;
   //    else if (state == S_RUN && tmp)
   //      state <= S_DONE;
   //    else if (state == S_DONE && output_ready)
   //      state <= S_IDLE;
   // end
   
   always @(posedge clock) begin
      if (reset)
        state <= S_IDLE;
      else if (state == S_IDLE && input_valid)
        state <= S_RUN;
      else if (state == S_RUN && tmp)
        state <= S_DONE;
      else if (state == S_DONE && output_ready)
        state <= S_IDLE;
   end
   

   // always @(posedge clock) begin
   //    if (state == S_IDLE && input_valid) begin
   //       gcd <= 0;
   //       tmp <= 1;
         
         
   //       product <= x*y;
         
   //    end else if (state == S_RUN) begin  
        
   //       if (tmp) begin
   //        gcd = gcd + product;
   //        tmp = 0;
         
   //       end
   //    end
   // end

endmodule // GCDMMIOBlackBox
