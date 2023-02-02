module MACMMIOBlackBox
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
    output reg [WIDTH-1:0] mac,
    output                 busy
    );

    localparam S_IDLE = 2'b00, S_RUN = 2'b01, S_DONE = 2'b10;

    reg [1:0]               state;
    reg [1:0]               done;
    reg [WIDTH-1:0]         prod;

    assign input_ready = state == S_IDLE;
    assign output_valid = state == S_DONE;
    assign busy = state != S_IDLE;

    always @(posedge clock) begin
        if (reset)
            state <= S_IDLE;
        else if (state == S_IDLE && input_valid)
            state <= S_RUN;
        else if (state == S_RUN && done == 1)
            state <= S_DONE;
        else if (state == S_DONE && output_ready)
            state <= S_IDLE;
    end

    always @(posedge clock) begin
        if (state == S_IDLE && input_valid) begin
            done <= 0;
            
        end else if (state == S_RUN) begin  
            prod <= x*y;
            mac <= mac + prod;
            done <= 1;
        end
   end



endmodule