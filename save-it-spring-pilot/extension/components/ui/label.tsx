import * as React from "react";
import { cn } from "../../lib/utils";

export const Label = React.forwardRef<
  HTMLLabelElement,
  React.LabelHTMLAttributes<HTMLLabelElement>
>(({ className, ...props }, ref) => (
  <label
    ref={ref}
    className={cn(
      "text-[11px] font-medium leading-none text-foreground/75 select-none tracking-tight",
      className
    )}
    {...props}
  />
));
Label.displayName = "Label";
